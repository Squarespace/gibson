package org.ardverk.gibson.transport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.ardverk.gibson.DatastoreFactory;
import org.ardverk.gibson.Event;
import org.ardverk.gibson.Gibson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.code.morphia.Datastore;
import com.mongodb.DBTCPConnector;
import com.mongodb.Mongo;
import com.mongodb.MongoURI;

/**
 * An implementation of {@link Transport} that is backed by MongoDB.
 */
public class MongoTransport implements Transport {
  
  private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(
      new TransportThreadFactory(MongoTransport.class));
  
  private static final Logger LOG = LoggerFactory.getLogger(MongoTransport.class);
  
  private List<Event> queue = new ArrayList<Event>();
  
  private final MongoURI uri;
  
  private final String database;
  
  private boolean open = true;
  
  private boolean connected = false;
  
  private Mongo mongo = null;
  
  private Future<?> future = null;
  
  public MongoTransport(MongoURI uri, String database) {
    this.uri = uri;
    this.database = database;
  }

  @Override
  public synchronized boolean isConnected() {
    if (!connected) {
      return false;
    }
    
    if (mongo == null) {
      return false;
    }
    
    DBTCPConnector connector = mongo.getConnector();
    if (!connector.isOpen()) {
      return false;
    }
    
    return true;
  }

  @Override
  public synchronized void connect() throws IOException {
    if (!open) {
      throw new IOException("closed");
    }
    
    if (connected) {
      throw new IOException("connected");
    }
    
    if (LOG.isInfoEnabled()) {
      LOG.info(Gibson.MARKER, "Connecting to: " + uri);
    }
    
    this.mongo = new Mongo(uri);
    
    DatastoreFactory factory = new DatastoreFactory(mongo);
    final Datastore datastore = factory.createDatastore(database);
    
    Runnable task = new Runnable() {
      @Override
      public void run() {
        try {
          process(datastore);
        } catch (InterruptedException err) {
          LOG.error(Gibson.MARKER, "InterruptedException", err);
        } finally {
          destroy(mongo);
        }
      }
    };
    
    this.future = EXECUTOR.submit(task);
    this.connected = true;
  }
  
  private void destroy(Mongo mongo) {
    try {
      close();
    } finally {
      mongo.close();
    }
  }
  
  @Override
  public synchronized void close() {
    if (open) {
      open = false;
      connected = false;
      
      if (future != null) {
        future.cancel(true);
      }
      
      notifyAll();
    }
  }
  
  private void process(Datastore datastore) throws InterruptedException {
    
    while (true) {
      
      List<Event> events = null;
      
      synchronized (this) {
        while (open && queue.isEmpty()) {
          wait();
        }
        
        if (!open) {
          break;
        }
        
        events = queue;
        queue = new ArrayList<Event>();
      }
      
      try {
        datastore.save(events);
      } catch (Exception err) {
        LOG.error(Gibson.MARKER, "Exception", err);
      }
    }
  }
  
  @Override
  public void send(Event event) {
    if (event == null) {
      throw new NullPointerException("event");
    }
    
    synchronized (this) {
      if (open && isConnected()) {
        queue.add(event);
        notifyAll();
      }
    }
  }
  
  private static class TransportThreadFactory implements ThreadFactory {

    private final AtomicInteger counter = new AtomicInteger();
    
    private final String name;
    
    public TransportThreadFactory(Class<?> clazz) {
      this.name = clazz.getSimpleName() + "Thread";
    }
    
    @Override
    public Thread newThread(Runnable r) {
      Thread thread = new Thread(r, name + "-" + counter.getAndIncrement());
      thread.setDaemon(true);
      return thread;
    }
  }
}