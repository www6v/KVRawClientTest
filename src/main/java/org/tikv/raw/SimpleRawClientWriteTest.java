package org.tikv.raw;

import com.flipkart.lois.channel.api.Channel;
import com.flipkart.lois.channel.exceptions.ChannelClosedException;
import com.flipkart.lois.channel.impl.BufferedChannel;
import org.apache.log4j.Logger;
import org.tikv.common.TiConfiguration;
import org.tikv.common.TiSession;
import shade.com.google.protobuf.ByteString;

import java.util.Date;
import java.util.Random;


/// 没有多线程
public class SimpleRawClientWriteTest {
//  private static final String PD_ADDRESS = "127.0.0.1:2379";
  private static final String PD_ADDRESS = "172.16.22.140:2379,172.16.22.141:2379,172.16.22.142:2379";

  private static final int DOCUMENT_SIZE = 1 << 10;
//  private static final int NUM_COLLECTIONS = 10;
//  private static final int NUM_DOCUMENTS = 100;
//  private static final int NUM_READERS = 1;
  private static final int NUM_WRITERS = 1;
  private static final Logger logger = Logger.getLogger("Main");

//  private static List<Kvrpcpb.KvPair> scan(RawKVClient client, String collection) {
//    return client.scan(ByteString.copyFromUtf8(collection), 100);
//  }

  private static void put(RawKVClient client, String collection, String key, String value) {
    client.put(ByteString.copyFromUtf8(String.format("%s#%s", collection, key)),
            ByteString.copyFromUtf8(value));
    System.out.println("put sussess   :  " + new Date().toString());
  }

//  private static class ReadAction {
//    String collection;
//
//    ReadAction(String collection) {
//      this.collection = collection;
//    }
//  }

  private static class WriteAction {
    String collection;
    String key;
    String value;

    WriteAction(String collection, String key, String value) {
      this.collection = collection;
      this.key = key;
      this.value = value;
    }
  }

  public static void main(String[] args) {

    TiConfiguration conf = TiConfiguration.createRawDefault(PD_ADDRESS);
    TiSession session = TiSession.create(conf);

    Channel<Long> writeTimes = new BufferedChannel<>(NUM_WRITERS);
    Channel<WriteAction> writeActions = new BufferedChannel<>(NUM_WRITERS);


//    new Thread(() -> {
//      Random rand = new Random(System.nanoTime());
//      while (true) {
//        try {
//
//            writeActions.send(new WriteAction(String.format("collection-%d", 1),
//                    String.format("%d", 1),
//                    makeTerm(rand, DOCUMENT_SIZE)));
//        } catch (InterruptedException e) {
//          logger.warn("WriteAction Interrupted");
//          return;
//        } catch (ChannelClosedException e) {
//          logger.warn("Channel has closed");
//          return;
//        }
//      }
//    }).start();


    while (true) {
      RawKVClient client;
      try {
        client = session.createRawClient();
      } catch (Exception e) {
        logger.fatal("error connecting to kv store: ", e);
        continue;
      }

      put(client, "collection", "key", "value");

      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
//      runWrite(client, writeActions, writeTimes);
//      System.out.println("batch write success");
    }

//    for (int i = 0; i < NUM_READERS; i++) {
//      RawKVClient client;
//      try {
//        client = session.createRawClient();
//      } catch (Exception e) {
//        logger.fatal("error connecting to kv store: ", e);
//        continue;
//      }
//      runRead(client, readActions, readTimes);
//    }

//    analyze("R", readTimes);
//    analyze("W", writeTimes);

//    System.out.println("Hello World!");
//    while (true) ;
  }

  private static void resolve(Channel<Long> timings, long start) {
    try {
      timings.send(System.nanoTime() - start);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.out.println("Current thread interrupted. Test fail.");
    } catch (ChannelClosedException e) {
      logger.warn("Channel has closed");
    }
  }

  private static void runWrite(RawKVClient client, Channel<WriteAction> action, Channel<Long> timings) {
    new Thread(() -> {
      WriteAction writeAction;
      try {
        while ((writeAction = action.receive()) != null) {
          long start = System.nanoTime();
          put(client, writeAction.collection, writeAction.key, writeAction.value);
          resolve(timings, start);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        System.out.println("Current thread interrupted. Test fail.");
      } catch (ChannelClosedException e) {
        logger.warn("Channel has closed");
      }
    }).start();
  }


  private static final char[] LETTER_BYTES = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

  private static String makeTerm(Random rand, int n) {
    char[] b = new char[n];
    for (int i = 0; i < n; i++) {
      b[i] = LETTER_BYTES[rand.nextInt(LETTER_BYTES.length)];
    }
    return String.valueOf(b);
  }

  private static void analyze(String label, Channel<Long> queue) {
    new Thread(() -> {
      long start = System.currentTimeMillis(), end;
      long total = 0;
      int count = 0;
      System.out.println("start label " + label);
      while (true) {
        try {
          total += queue.receive() / 1000;
          count++;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          System.out.println("Current thread interrupted. Test fail.");
        } catch (ChannelClosedException e) {
          logger.warn("Channel has closed");
          return;
        }
        end = System.currentTimeMillis();
        if (end - start > 1000) {
          System.out.println(String.format("[%s] % 6d total updates, avg = % 9d us\n", label, count, total / count));
          total = 0;
          count = 0;
          start = end;
        }
      }
    }).start();
  }
}
