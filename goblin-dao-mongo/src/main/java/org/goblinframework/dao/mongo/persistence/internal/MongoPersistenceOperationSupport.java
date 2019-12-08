package org.goblinframework.dao.mongo.persistence.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.mongodb.reactivestreams.client.Success;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.goblinframework.core.conversion.ConversionUtils;
import org.goblinframework.core.monitor.FlightExecutor;
import org.goblinframework.core.monitor.FlightRecorder;
import org.goblinframework.core.reactor.*;
import org.goblinframework.core.util.GoblinReferenceCount;
import org.goblinframework.core.util.MapUtils;
import org.goblinframework.core.util.NumberUtils;
import org.goblinframework.dao.mapping.EntityRevisionField;
import org.goblinframework.dao.ql.Criteria;
import org.goblinframework.dao.ql.Query;
import org.goblinframework.dao.ql.Update;
import org.goblinframework.database.mongo.bson.BsonConversionService;
import org.goblinframework.database.mongo.eql.MongoCriteriaTranslator;
import org.goblinframework.database.mongo.eql.MongoQueryTranslator;
import org.goblinframework.database.mongo.eql.MongoUpdateTranslator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.util.LinkedMultiValueMap;
import reactor.core.scheduler.Scheduler;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

abstract public class MongoPersistenceOperationSupport<E, ID> extends MongoPersistenceConversionSupport<E, ID> {

  protected final MongoCriteriaTranslator criteriaTranslator;
  protected final MongoQueryTranslator queryTranslator;
  protected final MongoUpdateTranslator updateTranslator;

  protected MongoPersistenceOperationSupport() {
    this.criteriaTranslator = MongoCriteriaTranslator.INSTANCE;
    this.queryTranslator = MongoQueryTranslator.INSTANCE;
    this.updateTranslator = MongoUpdateTranslator.INSTANCE;
  }

  private <T> SingleResultPublisher<T> createSingleResultPublisher() {
    return new SingleResultPublisher<>(null);
  }

  private <T> MultipleResultsPublisher<T> createMultipleResultsPublisher() {
    return new MultipleResultsPublisher<>(null);
  }

  public void insert(@NotNull final E entity) {
    __insert(entity);
  }

  public void inserts(@NotNull final Collection<E> entities) {
    __inserts(entities);
  }

  @Nullable
  public E load(@NotNull final ID id) {
    return __load(id, ReadPreference.primary());
  }

  @NotNull
  public Map<ID, E> loads(@NotNull Collection<ID> ids) {
    return __loads(ids, ReadPreference.primary());
  }

  public boolean exists(@NotNull final ID id) {
    Publisher<Boolean> publisher = __exists(id, ReadPreference.primary());
    BlockingMonoSubscriber<Boolean> subscriber = new BlockingMonoSubscriber<>();
    publisher.subscribe(subscriber);
    return ConversionUtils.toBoolean(subscriber.block());
  }

  @Nullable
  public E replace(@NotNull final E entity) {
    Publisher<E> publisher = __replace(entity);
    return new BlockingMonoSubscriber<E>().subscribe(publisher).block();
  }

  @Nullable
  public E upsert(@NotNull final E entity) {
    Publisher<E> publisher = __upsert(entity);
    return new BlockingMonoSubscriber<E>().subscribe(publisher).block();
  }

  public boolean remove(@NotNull final ID id) {
    Publisher<Boolean> publisher = __remove(id);
    Boolean deleted = new BlockingMonoSubscriber<Boolean>().subscribe(publisher).block();
    return ConversionUtils.toBoolean(deleted);
  }

  public long removes(@NotNull final Collection<ID> ids) {
    Publisher<Long> publisher = __removes(ids);
    Long deletedCount = new BlockingMonoSubscriber<Long>().subscribe(publisher).block();
    return NumberUtils.toLong(deletedCount);
  }

  /**
   * Insert the specified entity into mongo database.
   *
   * @param entity The entity object to be inserted.
   */
  final public void __insert(@NotNull final E entity) {
    beforeInsert(entity);
    BsonDocument document = (BsonDocument) BsonConversionService.toBson(entity);
    MongoNamespace namespace = getEntityNamespace(entity);
    MongoCollection<BsonDocument> collection = getMongoCollection(namespace);
    collection = collection.withWriteConcern(WriteConcern.ACKNOWLEDGED);
    Publisher<Success> insertPublisher = collection.insertOne(document);
    BlockingMonoSubscriber<Success> subscriber = new BlockingMonoSubscriber<>();
    insertPublisher.subscribe(subscriber);
    subscriber.block();
    subscriber.dispose();
  }

  /**
   * Insert specified entities into mongo database.
   *
   * @param entities Entities to be inserted.
   */
  final public void __inserts(@NotNull final Collection<E> entities) {
    List<E> entityList = entities.stream().filter(Objects::nonNull).collect(Collectors.toList());
    if (entityList.isEmpty()) {
      return;
    }
    entityList.forEach(this::beforeInsert);
    LinkedMultiValueMap<MongoNamespace, E> groupedEntities = groupEntities(entityList);
    BlockingListPublisher<Success> publisher = new BlockingListPublisher<>(groupedEntities.size());
    FlightExecutor executor = FlightRecorder.currentFlightExecutor();
    groupedEntities.forEach((ns, es) -> {
      Scheduler scheduler = CoreScheduler.getInstance();
      scheduler.schedule(() -> {
        executor.execute(() -> {
          try {
            List<BsonDocument> documents = ((BsonArray) BsonConversionService.toBson(es)).stream()
                .map(e -> (BsonDocument) e).collect(Collectors.toList());
            MongoCollection<BsonDocument> collection = getMongoCollection(ns);
            collection = collection.withWriteConcern(WriteConcern.ACKNOWLEDGED);
            Publisher<Success> insertPublisher = collection.insertMany(documents);
            BlockingListSubscriber<Success> subscriber = new BlockingListSubscriber<>();
            insertPublisher.subscribe(subscriber);
            subscriber.block();
            subscriber.dispose();
          } catch (Throwable ex) {
            publisher.onError(ex);
          } finally {
            publisher.onComplete();
          }
        });
      });
    });
    publisher.block();
  }

  @Nullable
  final public E __load(@NotNull final ID id, @Nullable ReadPreference readPreference) {
    MongoNamespace namespace = getIdNamespace(id);
    MongoCollection<BsonDocument> collection = getMongoCollection(namespace);
    if (readPreference != null) {
      collection = collection.withReadPreference(readPreference);
    }
    Criteria criteria = Criteria.where("_id").is(id);
    Bson filter = criteriaTranslator.translate(criteria);
    FindPublisher<BsonDocument> findPublisher = collection.find(filter);
    BlockingListSubscriber<BsonDocument> subscriber = new BlockingListSubscriber<>();
    subscriber.subscribe(findPublisher);
    List<BsonDocument> documents = subscriber.block();
    subscriber.dispose();
    BsonDocument document = documents.stream().findFirst().orElse(null);
    return convertBsonDocument(document);
  }

  /**
   * Load entities of specified ids from mongo database (parallel mode).
   *
   * @param ids            Entity ids.
   * @param readPreference Read preference, use default in case of null passed in.
   * @return Loaded entities.
   */
  @NotNull
  final public Map<ID, E> __loads(@NotNull final Collection<ID> ids,
                                  @Nullable ReadPreference readPreference) {
    return __loads(ids, readPreference, true);
  }

  /**
   * Load entities of specified ids from mongo database.
   *
   * @param ids            Entity ids.
   * @param readPreference Read preference, use default in case of null passed in.
   * @param parallel       Parallel mode or not
   * @return Loaded entities.
   */
  @NotNull
  final public Map<ID, E> __loads(@NotNull final Collection<ID> ids,
                                  @Nullable ReadPreference readPreference,
                                  boolean parallel) {
    List<ID> idList = ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    if (idList.isEmpty()) {
      return Collections.emptyMap();
    }
    if (idList.size() == 1) {
      ID id = idList.iterator().next();
      E entity = __load(id, readPreference);
      if (entity == null) {
        return Collections.emptyMap();
      } else {
        Map<ID, E> entities = new LinkedHashMap<>();
        entities.put(id, entity);
        return entities;
      }
    }
    List<E> entityList;
    LinkedMultiValueMap<MongoNamespace, ID> groupIds = groupIds(idList);
    if (groupIds.size() > 1 && parallel) {
      BlockingListPublisher<E> publisher = new BlockingListPublisher<>(groupIds.size());
      FlightExecutor executor = FlightRecorder.currentFlightExecutor();
      groupIds.forEach((ns, ds) -> {
        Scheduler scheduler = CoreScheduler.getInstance();
        scheduler.schedule(() -> executor.execute(() -> {
          try {
            Criteria criteria;
            if (ds.size() == 1) {
              ID id = ds.iterator().next();
              criteria = Criteria.where("_id").is(id);
            } else {
              criteria = Criteria.where("_id").in(ds);
            }
            Query query = Query.query(criteria);
            __query(query, ns, readPreference).forEach(publisher::onNext);
          } catch (Throwable ex) {
            publisher.onError(ex);
          } finally {
            publisher.onComplete();
          }
        }));
      });
      entityList = publisher.block();
    } else {
      entityList = new ArrayList<>();
      groupIds.forEach((ns, ds) -> {
        Criteria criteria;
        if (ds.size() == 1) {
          ID id = ds.iterator().next();
          criteria = Criteria.where("_id").is(id);
        } else {
          criteria = Criteria.where("_id").in(ds);
        }
        Query query = Query.query(criteria);
        entityList.addAll(__query(query, ns, readPreference));
      });
    }
    Map<ID, E> entities = new LinkedHashMap<>();
    entityList.forEach(e -> {
      ID id = getEntityId(e);
      entities.put(id, e);
    });
    return MapUtils.resort(entities, idList);
  }

  @NotNull
  final public Publisher<Boolean> __exists(@NotNull ID id, @Nullable ReadPreference readPreference) {
    MongoNamespace namespace = getIdNamespace(id);
    Criteria criteria = Criteria.where("_id").is(id);
    Bson filter = criteriaTranslator.translate(criteria);
    MongoDatabase database = mongoClient.getDatabase(namespace.getDatabaseName());
    MongoCollection<BsonDocument> collection = database.getCollection(namespace.getCollectionName(), BsonDocument.class);
    if (readPreference != null) {
      collection = collection.withReadPreference(readPreference);
    }
    SingleResultPublisher<Boolean> publisher = createSingleResultPublisher();
    collection.countDocuments(filter).subscribe(new Subscriber<Long>() {
      @Override
      public void onSubscribe(Subscription s) {
        s.request(1);
      }

      @Override
      public void onNext(Long aLong) {
        long count = NumberUtils.toLong(aLong);
        publisher.complete(count > 0, null);
      }

      @Override
      public void onError(Throwable t) {
        publisher.complete(null, t);
      }

      @Override
      public void onComplete() {
      }
    });
    return publisher;
  }

  @NotNull
  final public Publisher<E> __replace(@NotNull final E entity) {
    SingleResultPublisher<E> publisher = createSingleResultPublisher();
    ID id = getEntityId(entity);
    if (id == null) {
      String errMsg = "Id must not be null when executing replace operation";
      publisher.complete(null, new IllegalArgumentException(errMsg));
      return publisher;
    }
    long millis = System.currentTimeMillis();
    touchUpdateTime(entity, millis);

    Criteria criteria = Criteria.where("_id").is(id);
    EntityRevisionField revisionField = entityMapping.revisionField;
    if (revisionField != null) {
      Object revision = revisionField.getField().get(entity);
      if (revision != null) {
        // revision specified, use it for optimistic concurrency checks
        criteria = criteria.and(revisionField.getName()).is(revision);
      }
    }
    Bson filter = criteriaTranslator.translate(criteria);

    Update update = new Update();
    entityMapping.updateTimeFields.forEach(ut -> {
      Object val = ut.getField().get(entity);
      if (val != null) {
        update.set(ut.getName(), val);
      }
    });
    entityMapping.normalFields.forEach(n -> {
      Object val = n.getField().get(entity);
      if (val != null) {
        update.set(n.getName(), val);
      }
    });
    if (revisionField != null) {
      update.inc(revisionField.getName(), 1);
    }

    if (update.export().isEmpty()) {
      String errMsg = "There is nothing field(s) found when executing replace operation";
      publisher.complete(null, new IllegalArgumentException(errMsg));
      return publisher;
    }

    MongoNamespace namespace = getIdNamespace(id);
    MongoDatabase database = mongoClient.getDatabase(namespace.getDatabaseName());
    MongoCollection<BsonDocument> collection = database.getCollection(namespace.getCollectionName(), BsonDocument.class);
    FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER);
    collection.withWriteConcern(WriteConcern.ACKNOWLEDGED)
        .findOneAndUpdate(filter, updateTranslator.translate(update), options)
        .subscribe(new Subscriber<BsonDocument>() {
          @Override
          public void onSubscribe(Subscription s) {
            s.request(1);
          }

          @Override
          public void onNext(BsonDocument bsonDocument) {
            E replaced = convertBsonDocument(bsonDocument);
            publisher.complete(replaced, null);
          }

          @Override
          public void onError(Throwable t) {
            publisher.complete(null, t);
          }

          @Override
          public void onComplete() {
          }
        });
    return publisher;
  }

  @NotNull
  final public Publisher<E> __upsert(@NotNull final E entity) {
    ID id = getEntityId(entity);
    if (id == null) {
      // No id field specified, redirect to insert operation
      __insert(entity);
      throw new UnsupportedOperationException("TODO");
    }
    long millis = System.currentTimeMillis();
    touchUpdateTime(entity, millis);

    Criteria criteria = Criteria.where("_id").is(id);
    EntityRevisionField revisionField = entityMapping.revisionField;
    if (revisionField != null) {
      Object revision = revisionField.getField().get(entity);
      if (revision != null) {
        // revision specified, use it for optimistic concurrency checks
        criteria = criteria.and(revisionField.getName()).is(revision);
      }
    }
    Bson filter = criteriaTranslator.translate(criteria);

    Update update = new Update();
    entityMapping.updateTimeFields.forEach(ut -> {
      Object val = ut.getField().get(entity);
      if (val != null) {
        update.set(ut.getName(), val);
      }
    });
    entityMapping.normalFields.forEach(n -> {
      Object val = n.getField().get(entity);
      if (val != null) {
        update.set(n.getName(), val);
      }
    });
    if (revisionField != null) {
      update.inc(revisionField.getName(), 1);
    }
    if (update.export().isEmpty()) {
      // There is nothing field(s) found when executing upsert operation, direct to insert
      __insert(entity);
      throw new UnsupportedOperationException("TODO");
    }
    MongoNamespace namespace = getIdNamespace(id);
    MongoDatabase database = mongoClient.getDatabase(namespace.getDatabaseName());
    MongoCollection<BsonDocument> collection = database.getCollection(namespace.getCollectionName(), BsonDocument.class);
    FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER);
    SingleResultPublisher<E> publisher = createSingleResultPublisher();
    collection.withWriteConcern(WriteConcern.ACKNOWLEDGED)
        .findOneAndUpdate(filter, updateTranslator.translate(update), options)
        .subscribe(new Subscriber<BsonDocument>() {
          @Override
          public void onSubscribe(Subscription s) {
            s.request(1);
          }

          @Override
          public void onNext(BsonDocument bsonDocument) {
            E replaced = convertBsonDocument(bsonDocument);
            publisher.complete(replaced, null);
          }

          @Override
          public void onError(Throwable t) {
            publisher.complete(null, t);
          }

          @Override
          public void onComplete() {
          }
        });
    return publisher;
  }

  @NotNull
  final public Publisher<Boolean> __remove(@NotNull final ID id) {
    AtomicLong deletedCount = new AtomicLong();
    SingleResultPublisher<Boolean> publisher = createSingleResultPublisher();
    __removes(Collections.singleton(id)).subscribe(new Subscriber<Long>() {
      @Override
      public void onSubscribe(Subscription s) {
        s.request(1);
      }

      @Override
      public void onNext(Long aLong) {
        deletedCount.set(NumberUtils.toLong(aLong));
      }

      @Override
      public void onError(Throwable t) {
        publisher.complete(null, t);
      }

      @Override
      public void onComplete() {
        publisher.complete(deletedCount.get() > 0, null);
      }
    });
    return publisher;
  }

  @NotNull
  final public Publisher<Long> __removes(@NotNull final Collection<ID> ids) {
    SingleResultPublisher<Long> publisher = createSingleResultPublisher();
    List<ID> idList = ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    if (idList.isEmpty()) {
      publisher.complete(0L, null);
      return publisher;
    }
    LinkedMultiValueMap<MongoNamespace, ID> grouped = groupIds(idList);
    AtomicLong deletedCount = new AtomicLong();
    GoblinReferenceCount referenceCount = new GoblinReferenceCount(grouped.size());
    grouped.forEach((ns, ds) -> {
      MongoDatabase database = mongoClient.getDatabase(ns.getDatabaseName());
      MongoCollection<BsonDocument> collection = database.getCollection(ns.getCollectionName(), BsonDocument.class);
      Publisher<DeleteResult> deletePublisher;
      if (ds.size() == 1) {
        Criteria criteria = Criteria.where("_id").is(ds.iterator().next());
        Bson filter = criteriaTranslator.translate(criteria);
        deletePublisher = collection.withWriteConcern(WriteConcern.ACKNOWLEDGED).deleteOne(filter);
      } else {
        Criteria criteria = Criteria.where("_id").in(ds);
        Bson filter = criteriaTranslator.translate(criteria);
        deletePublisher = collection.withWriteConcern(WriteConcern.ACKNOWLEDGED).deleteMany(filter);
      }
      deletePublisher.subscribe(new Subscriber<DeleteResult>() {
        @Override
        public void onSubscribe(Subscription s) {
          s.request(1);
        }

        @Override
        public void onNext(DeleteResult deleteResult) {
          deletedCount.addAndGet(deleteResult.getDeletedCount());
        }

        @Override
        public void onError(Throwable t) {
          publisher.complete(null, t);
        }

        @Override
        public void onComplete() {
          if (referenceCount.release()) {
            publisher.complete(deletedCount.get(), null);
          }
        }
      });
    });
    return publisher;
  }

  @NotNull
  final protected Publisher<E> __executeQuery(@NotNull Query query, @NotNull MongoNamespace namespace, @Nullable ReadPreference readPreference) {
    MongoDatabase database = mongoClient.getDatabase(namespace.getDatabaseName());
    MongoCollection<BsonDocument> collection = database.getCollection(namespace.getCollectionName(), BsonDocument.class);
    if (readPreference != null) {
      collection = collection.withReadPreference(readPreference);
    }
    Bson filter = queryTranslator.toFilter(query);
    FindPublisher<BsonDocument> findPublisher = collection.find(filter);
    if (query.getLimit() != null) {
      findPublisher = findPublisher.limit(query.getLimit());
    }
    if (query.getSkip() != null) {
      findPublisher = findPublisher.skip(query.getSkip());
    }
    Bson projection = queryTranslator.toProjection(query);
    if (projection != null) {
      findPublisher = findPublisher.projection(projection);
    }
    Bson sort = queryTranslator.toSort(query);
    if (sort != null) {
      findPublisher = findPublisher.sort(sort);
    }
    MultipleResultsPublisher<E> publisher = createMultipleResultsPublisher();
    findPublisher.subscribe(new Subscriber<BsonDocument>() {
      @Override
      public void onSubscribe(Subscription s) {
        s.request(Long.MAX_VALUE);
      }

      @Override
      public void onNext(BsonDocument bsonDocument) {
        try {
          E findResult = convertBsonDocument(bsonDocument);
          Objects.requireNonNull(findResult);
          publisher.onNext(findResult);
        } catch (Exception ex) {
          publisher.complete(ex);
        }
      }

      @Override
      public void onError(Throwable t) {
        publisher.complete(t);
      }

      @Override
      public void onComplete() {
        publisher.complete(null);
      }
    });
    return publisher;
  }

  /**
   * Execute query operation on specified mongo namespace.
   *
   * @param query          Query to be executed.
   * @param namespace      Mongo namespace.
   * @param readPreference Read preference, use default in case of null passed in.
   * @return Query result list.
   */
  @NotNull
  final public List<E> __query(@NotNull final Query query,
                               @NotNull final MongoNamespace namespace,
                               @Nullable final ReadPreference readPreference) {
    MongoCollection<BsonDocument> collection = getMongoCollection(namespace);
    if (readPreference != null) {
      collection = collection.withReadPreference(readPreference);
    }
    Bson filter = queryTranslator.toFilter(query);
    FindPublisher<BsonDocument> findPublisher = collection.find(filter);
    if (query.getLimit() != null) {
      findPublisher = findPublisher.limit(query.getLimit());
    }
    if (query.getSkip() != null) {
      findPublisher = findPublisher.skip(query.getSkip());
    }
    Bson projection = queryTranslator.toProjection(query);
    if (projection != null) {
      findPublisher = findPublisher.projection(projection);
    }
    Bson sort = queryTranslator.toSort(query);
    if (sort != null) {
      findPublisher = findPublisher.sort(sort);
    }
    BlockingListSubscriber<BsonDocument> subscriber = new BlockingListSubscriber<>();
    subscriber.subscribe(findPublisher);
    List<BsonDocument> documents = subscriber.block();
    subscriber.dispose();
    return convertBsonDocuments(documents);
  }
}
