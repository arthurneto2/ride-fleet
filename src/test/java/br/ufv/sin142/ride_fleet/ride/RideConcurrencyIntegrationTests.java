package br.ufv.sin142.ride_fleet.ride;

import br.ufv.sin142.ride_fleet.driver.Driver;
import br.ufv.sin142.ride_fleet.driver.DriverStatus;
import br.ufv.sin142.ride_fleet.passenger.Passenger;
import br.ufv.sin142.ride_fleet.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A prova de que a atribuicao de motorista nao sofre perda de atualizacao.
 *
 * Sem o UPDATE atomico condicional, varias threads leem o mesmo motorista como
 * AVAILABLE, todas escrevem IN_RIDE e o mesmo motorista termina com varias
 * corridas. Estes testes falhariam.
 *
 * LIMITE HONESTO: H2 em memoria e um unico JVM. Os testes exercitam o SQL e as
 * fronteiras de transacao, mas NAO provam exclusao entre processos -
 * MODE=PostgreSQL e compatibilidade de SQL, nao de motor de lock. A prova real e
 * a Semana 2, com dois conteineres contra PostgreSQL. Isso deve ser dito na
 * apresentacao, nao mascarado.
 *
 * A classe NAO pode ser @Transactional: uma transacao unica compartilhada com as
 * threads invalidaria todo o teste.
 */
class RideConcurrencyIntegrationTests extends IntegrationTestBase {

    @Autowired
    private RideService rideService;

    private Passenger givenPassenger() {
        return passengerRepository.save(Passenger.builder()
                .name("Passageiro Concorrencia")
                .email("concorrencia@exemplo.com")
                .phone("31999990000")
                .password("hash")
                .build());
    }

    private void givenAvailableDrivers(int count) {
        for (int i = 0; i < count; i++) {
            driverRepository.save(Driver.builder()
                    .name("Motorista " + i)
                    .vehiclePlate("CNC-%04d".formatted(i))
                    .email("motorista%d@concorrencia.com".formatted(i))
                    .password("hash")
                    .status(DriverStatus.AVAILABLE)
                    .active(true)
                    .build());
        }
    }

    private List<UUID> givenPendingRides(Passenger passenger, int count) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Ride ride = rideRepository.save(Ride.builder()
                    .passenger(passenger)
                    .status(RideStatus.REQUEST)
                    .originLatitude(-19.2012).originLongitude(-46.2231).originAddress("A")
                    .destinationLatitude(-19.2045).destinationLongitude(-46.2290).destinationAddress("B")
                    .price(new BigDecimal("18.50"))
                    .etaSeconds(360)
                    .logicalTimestamp(0L)
                    .awaitingDelegation(false)
                    .build());
            ids.add(ride.getId());
        }
        return ids;
    }

    /** Dispara tryAssignDriver concorrentemente e conta quantas atribuicoes venceram. */
    private int assignConcurrently(List<UUID> rideIds, int threads) throws InterruptedException {
        AtomicInteger assigned = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            final UUID rideId = rideIds.get(i % rideIds.size());
            pool.submit(() -> {
                try {
                    start.await();
                    if (rideService.tryAssignDriver(rideId)) {
                        assigned.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    // perder a disputa e um resultado valido (lock, versao otimista);
                    // o que importa e o estado final do banco
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        return assigned.get();
    }

    @Test
    @DisplayName("Um unico motorista nunca e atribuido a duas corridas")
    void singleDriverShouldNeverBeAssignedTwice() throws Exception {
        Passenger passenger = givenPassenger();
        givenAvailableDrivers(1);
        List<UUID> rides = givenPendingRides(passenger, 20);

        assignConcurrently(rides, 20);

        long matched = rideRepository.findByStatus(RideStatus.MATCH).size();
        assertThat(matched).isEqualTo(1);

        List<Driver> busy = driverRepository.findByStatusAndActiveTrue(DriverStatus.IN_RIDE);
        assertThat(busy).hasSize(1);
    }

    @Test
    @DisplayName("Com dez motoristas e dez corridas, cada motorista recebe uma so")
    void eachDriverShouldGetExactlyOneRide() throws Exception {
        Passenger passenger = givenPassenger();
        givenAvailableDrivers(10);
        List<UUID> rides = givenPendingRides(passenger, 10);

        assignConcurrently(rides, 20);

        List<Ride> matched = rideRepository.findByStatus(RideStatus.MATCH);
        assertThat(matched).hasSize(10);

        // nenhum motorista repetido entre as dez corridas atribuidas
        long distinctDrivers = matched.stream()
                .map(ride -> ride.getDriver().getId())
                .distinct()
                .count();
        assertThat(distinctDrivers).isEqualTo(10);
    }

    @Test
    @DisplayName("Mais corridas do que motoristas: o excedente permanece no pool")
    void surplusRidesShouldStayInPool() throws Exception {
        Passenger passenger = givenPassenger();
        givenAvailableDrivers(3);
        List<UUID> rides = givenPendingRides(passenger, 12);

        assignConcurrently(rides, 12);

        assertThat(rideRepository.findByStatus(RideStatus.MATCH)).hasSize(3);
        assertThat(rideRepository.findByStatus(RideStatus.REQUEST)).hasSize(9);
        assertThat(driverRepository.findByStatusAndActiveTrue(DriverStatus.AVAILABLE)).isEmpty();
    }

    @Test
    @DisplayName("Nenhum motorista fica ocupado sem corrida atribuida")
    void noDriverShouldBeLeftBusyWithoutARide() throws Exception {
        Passenger passenger = givenPassenger();
        givenAvailableDrivers(5);
        List<UUID> rides = givenPendingRides(passenger, 5);

        assignConcurrently(rides, 20);

        // e a falha simetrica que o SKIP LOCKED sozinho nao evita: duas instancias
        // processando a MESMA corrida reservariam dois motoristas, deixando um
        // ocupado sem corrida. O lock da corrida e o que impede isso.
        long busyDrivers = driverRepository.findByStatusAndActiveTrue(DriverStatus.IN_RIDE).size();
        long ridesWithDriver = rideRepository.findByStatus(RideStatus.MATCH).size();

        assertThat(busyDrivers).isEqualTo(ridesWithDriver);
    }
}
