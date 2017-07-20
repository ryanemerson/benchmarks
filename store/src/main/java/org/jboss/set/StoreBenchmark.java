package org.jboss.set;

import org.infinispan.AdvancedCache;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jgroups.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Ryan Emerson
 */
public class StoreBenchmark {

   private final String config;
   private final String cacheName;
   private final int numberOfExecutions;
   private final int numberOfThreads;
   private final int totalOps;
   private final double readPercentage;
   private final boolean putAllOp;
   private final int putAllOpSize;

   StoreBenchmark(String config, String cacheName, int numberOfExecutions, int numberOfThreads, int totalOps, double readPercentage, boolean putAllOp, int putAllOpSize) {
      this.config = config;
      this.cacheName = cacheName;
      this.numberOfExecutions = numberOfExecutions;
      this.numberOfThreads = numberOfThreads;
      this.totalOps = totalOps;
      this.readPercentage = readPercentage;
      this.putAllOp = putAllOp;
      this.putAllOpSize = putAllOpSize;
   }

   private void run() throws Exception {
      List<TestResult> results = new ArrayList<>(numberOfExecutions);
      for (int i = 0; i < numberOfExecutions; i++)
         results.add(executeTest());

      // output results
      double averageTt = results.stream().mapToLong(test -> test.timeTaken).average().getAsDouble();
      long ttMilli = TimeUnit.NANOSECONDS.toMillis((long) averageTt);
      double averageWrites = results.stream().mapToLong(test -> test.writes).average().getAsDouble();
      double averageReads = results.stream().mapToLong(test -> test.reads).average().getAsDouble();

      String output = String.format("%s | Average Duration %s ms | Average Writes %s | Average Reads %s",
                cacheName, ttMilli, (long) averageWrites, (long) averageReads);
      System.out.println(output);
   }

   private TestResult executeTest() throws Exception {
      ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
      EmbeddedCacheManager manager = new DefaultCacheManager(config);
      try {
         AdvancedCache<Object, Object> cache = manager.getCache(cacheName).getAdvancedCache();
         CountDownLatch complete = new CountDownLatch(numberOfThreads);
         AtomicInteger numberOfOperations = new AtomicInteger();
         AtomicInteger reads = new AtomicInteger();
         AtomicInteger writes = new AtomicInteger();

         long st = System.nanoTime();
         for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
               Map<Integer, TestObject> putAllMap = new HashMap<>();
               while (true) {
                  if (putAllOp) {
                     int ops = numberOfOperations.getAndIncrement();
                     if (ops >= totalOps) {
                        if (!putAllMap.isEmpty())
                           cache.putAll(putAllMap);
                        break;
                     }
                     putAllMap.put(ops, new TestObject(ops));
                     writes.incrementAndGet();
                     if (putAllMap.size() == putAllOpSize) {
                        cache.putAll(putAllMap);
                        putAllMap.clear();
                     }
                  } else {
                     int ops = numberOfOperations.getAndIncrement();
                     if (ops >= totalOps)
                        break;

                     boolean read = Util.tossWeightedCoin(readPercentage);
                     if (read) {
                        cache.get(1);
                        reads.incrementAndGet();
                     } else {
                        cache.put(ops, new TestObject(ops));
                        writes.incrementAndGet();
                     }
                  }
               }
               complete.countDown();
            });
         }
         complete.await();
         long tt = System.nanoTime() - st;
         return new TestResult(tt, writes.get(), reads.get());
      } finally {
         executorService.shutdown();
         manager.stop();
      }
   }

   static class TestResult {
      long timeTaken;
      int writes;
      int reads;

      TestResult(long timeTaken, int writes, int reads) {
         this.timeTaken = timeTaken;
         this.writes = writes;
         this.reads = reads;
      }
   }

   public static void main(String[] args) throws Exception {
      if (args.length == 0) throw new IllegalArgumentException("Expected infinispan version as arg[0]");
      String config = String.format("store-batch-benchmark-%s.xml", args[0]);
      int executions = 3;
      int threads = 20;
      int ops = 100000;
      double readPercentage = 0;
      boolean putAll = true;
      int putAllOps = 20;

      String[] testCaches = new String[] {"JdbcStringBasedStore", "JPAStore", "RocksDBStore"};

      System.out.println(String.format("Config %s | Number of executions %d | Threads %d | Total Operations %d | Read Percentage %s | putAllCommand=%s | putAllOps %d",
                                       config, executions, threads, ops, readPercentage, putAll, putAllOps));
      System.out.println("-------------------------------------------------------------------");
      for (String cache : testCaches)
         new StoreBenchmark(config, cache, executions, threads, ops, readPercentage, putAll, putAllOps).run();
   }
}
