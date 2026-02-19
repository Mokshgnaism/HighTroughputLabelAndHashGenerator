package org.example;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import generator.LabelGenerator;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

public class Main {
    public static final byte[] POISON = new byte[]{-1};

    public static void main(String[] args) throws IOException, InterruptedException {
        Long start = System.nanoTime();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/testdb");
        config.setUsername("postgres");
        config.setPassword("Mokshgna@123");

        config.setMaximumPoolSize(16);
        config.setAutoCommit(false);

        HikariDataSource ds = new HikariDataSource(config);

        ArrayBlockingQueue<byte[]> palletQueue = new ArrayBlockingQueue<>(200_000);
        ArrayBlockingQueue<byte[]> cartonQueue = new ArrayBlockingQueue<>(200_000);
        int noOfPallets = 10000;
        int cartonsPerPallet = 10;
        int unitsPerCarton = 50;

        int palletWorkers = 5;
        int unitWorkers = 5;
        int cartonWorkers = 5;

        int cartonDbs = 3;
        int unitDbs = 3;
        int palletDbs = 3;

        List<CompletableFuture<Void>> palletCFS = new ArrayList<>();
        List<CompletableFuture<Void>> cartonCFS = new ArrayList<>();
        List<CompletableFuture<Void>> unitCFS = new ArrayList<>();


        List<CompletableFuture<Void>> db1 = new ArrayList<>();

        int chunk = (noOfPallets + palletWorkers - 1) / palletWorkers;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();


        for (int i = 0; i < palletWorkers; i++) {
            int startIdx = i * chunk;
            int endIdx = Math.min(startIdx + chunk, noOfPallets);
            PipedOutputStream pos = new PipedOutputStream();
            PipedInputStream pis = new PipedInputStream(pos,1024*64);

            palletCFS.add(CompletableFuture.runAsync(() -> {
                try {
                    LabelGenerator.generatePalletIdsAndInsert(startIdx,endIdx,palletQueue,pos,"mokshu","abc123","mokshu123");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            },executor));

            db1.add(CompletableFuture.runAsync(() -> {
                try(var conn = ds.getConnection() ){
                    conn.setAutoCommit(false);
                    BaseConnection baseConnection = conn.unwrap(BaseConnection.class);
                    CopyManager copyManager = new CopyManager(baseConnection);
                    copyManager.copyIn("COPY pallets(ssic,hash,hash_prefix) FROM STDIN WITH CSV",pis);
                    conn.commit();
                }catch(Exception e){
                    e.printStackTrace();
                }

            },executor));

        }

        for(int i = 0; i < cartonWorkers; i++) {
            PipedOutputStream pos = new PipedOutputStream();
            PipedInputStream pis;
            pis = new PipedInputStream(pos,1024*64);
            cartonCFS.add(CompletableFuture.runAsync(() -> {
                try {
                    LabelGenerator.generateCartonIdsAndInsert(palletQueue,cartonQueue,pos,cartonsPerPallet,POISON);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            },executor));

            db1.add(CompletableFuture.runAsync(() -> {
                try(var conn = ds.getConnection()){
                    conn.setAutoCommit(false);
                    BaseConnection baseConnection = conn.unwrap(BaseConnection.class);
                    CopyManager copyManager = new CopyManager(baseConnection);
                    copyManager.copyIn("COPY cartons(serial_id,parent_pallet_id,hash,hash_prefix) FROM STDIN WITH CSV",pis);
                    conn.commit();
                }catch(Exception e){
                    throw new RuntimeException(e);
                }
            },executor));
        }

        for(int i=0;i<unitWorkers;i++){
            PipedOutputStream pos = new PipedOutputStream();
            PipedInputStream pis = new PipedInputStream(pos,1024*64);
            unitCFS.add(CompletableFuture.runAsync(() -> {
                try {
                    LabelGenerator.generateUnitIdsAndInsert(cartonQueue,unitsPerCarton,POISON,pos);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            },executor));

            db1.add(CompletableFuture.runAsync(() -> {
                try(var conn = ds.getConnection()){
                    conn.setAutoCommit(false);
                    BaseConnection baseConnection = conn.unwrap(BaseConnection.class);
                    CopyManager copyManager = new CopyManager(baseConnection);
                    copyManager.copyIn("COPY units(serial_id,parent_carton_id,hash,hash_prefix) FROM STDIN WITH CSV",pis);
                    conn.commit();
                }catch(Exception e){
                    throw new RuntimeException(e);
                }
            },executor));
        }

        CompletableFuture<Void> cfallpalets = CompletableFuture.allOf(palletCFS.toArray(new CompletableFuture[palletCFS.size()]));
        cfallpalets.join();
//        first phase completed place poison pills
        for(int i=0;i<cartonWorkers;i++){
            palletQueue.put(POISON);
        }

        CompletableFuture<Void>cfallcartons = CompletableFuture.allOf(cartonCFS.toArray(new CompletableFuture[cartonCFS.size()]));
        cfallcartons.join();
        for(int i=0;i<unitWorkers;i++){
            cartonQueue.put(POISON);
        }

        CompletableFuture<Void>cfallunits = CompletableFuture.allOf(unitCFS.toArray(new CompletableFuture[unitCFS.size()]));
        cfallunits.join();

        CompletableFuture<Void> alldbs = CompletableFuture.allOf(db1.toArray(new CompletableFuture[db1.size()]));
        alldbs.join();
        Long end =  System.nanoTime();
        System.out.println("Total execution time: "+(end-start)/1000000);
    }
}