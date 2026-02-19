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

// the pipeline is quite simple but yet effective .
// first off all it is not a pipeline in the regular language
/*
*           THIS PROJECT DOES THE FOLLOWING :
*   -> generates PALLET IDS, hashes them which are the base containers for products ,
*   -> generates cartonIds for the cartons and these cartons have a very specific role these are the first box inside the pallets
*   -> generates the unitids and hashes them as well these unit ids are the ids for products  which are inside the cartons
*   KEY CHARACHERSTIC:
*               -> it follows a complete streaming architecture . no blocking stage until and unless completely necessary
*               -> the architecture is as follows
*                       -> there are two functions
*                               -> one is generateAndInsertPalletIds which takes a pipedoutputstream . generates the ids and writes to the pos
*                               -> another is db inserter which has its own pipedinput stream whihc is connected to the pos from the above function .
*                               -> so as the labels are being generated they are being streamed to copy using a csv without lag
*                               -> now comes the next state . we need the ssics of pallets to generate the serialids of cartons
*                               -> so as we generate the pallet ids we insert them into a blocking queue
*                               -> then we spin up some other workers(to genrarte and insert cartons) which consume from the above blocking queue and do the same above two functions.
*                               -> we use poison placing to end the producer consumer loop.
*               -> benchmarking gave a superb  resultt of 8 seconds for a huge 5.51Million labels hashed and inserted into postgres
*               -> original SLA was 10k labels under 5 seconds .
*               -> will try to optimise more to get there .
*               -> there are some tradeoffs like Mac cloning(warm start for hashing function which requires cloning) or without warm start completely hash the payload .
*               -> and is using binary protocol that necessary when we are that close to the SLA is thequesiton and some byte array copys need to be checked in the Labelgenerator Class
*               -> they are the optimisations remaining .
* */