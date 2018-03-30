package org.apache.samza.system.framework.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.samza.config.MapConfig;
import org.apache.samza.system.EndOfStreamMessage;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.SystemStreamMetadata;
import org.apache.samza.system.SystemStreamPartition;
import org.apache.samza.system.inmemory.InMemorySystemConsumer;
import org.apache.samza.system.inmemory.InMemorySystemFactory;
import org.hamcrest.collection.IsIterableContainingInAnyOrder;
import org.hamcrest.collection.IsIterableContainingInOrder;

import static org.junit.Assert.*;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;


public class TaskAssert<T> {
  private String systemStream;
  private String systemName;

  public TaskAssert(String systemName, String systemStream) {
    this.systemStream = systemStream;
    this.systemName = systemName;
  }

  public static <T> TaskAssert<T> that(String systemName, String systemStream) {
    return new TaskAssert<>(systemName, systemStream);
  }

  public List<T> consume() throws InterruptedException {
    InMemorySystemFactory factory = new InMemorySystemFactory();
    Set<SystemStreamPartition> ssps = new HashSet<>();
    Set<String> streamNames = new HashSet<>();
    streamNames.add(systemStream);
    Map<String, SystemStreamMetadata> metadata =
        factory.getAdmin(systemName, new MapConfig()).getSystemStreamMetadata(streamNames);
    InMemorySystemConsumer consumer =
        (InMemorySystemConsumer) factory.getConsumer(systemName, new MapConfig(new HashMap<>()), null);
    metadata.get(systemStream).getSystemStreamPartitionMetadata().keySet().forEach(partition -> {
      SystemStreamPartition temp = new SystemStreamPartition(systemName, systemStream, partition);
      ssps.add(temp);
      consumer.register(temp, "0");
    });
    Map<SystemStreamPartition, List<IncomingMessageEnvelope>> output = consumer.poll(ssps, 10);
    return output.values()
        .stream()
        .flatMap(List::stream)
        .map(e -> (T) e.getMessage())
        .filter(e -> !(e instanceof EndOfStreamMessage))
        .collect(Collectors.toList());
  }

  public Map<Integer, List<T>> consumePartitions() throws InterruptedException {
    InMemorySystemFactory factory = new InMemorySystemFactory();
    Set<SystemStreamPartition> ssps = new HashSet<>();
    Set<String> streamNames = new HashSet<>();
    streamNames.add(systemStream);
    Map<String, SystemStreamMetadata> metadata =
        factory.getAdmin(systemName, new MapConfig()).getSystemStreamMetadata(streamNames);
    InMemorySystemConsumer consumer =
        (InMemorySystemConsumer) factory.getConsumer(systemName, new MapConfig(new HashMap<>()), null);
    metadata.get(systemStream).getSystemStreamPartitionMetadata().keySet().forEach(partition -> {
      SystemStreamPartition temp = new SystemStreamPartition(systemName, systemStream, partition);
      ssps.add(temp);
      consumer.register(temp, "0");
    });
    Map<SystemStreamPartition, List<IncomingMessageEnvelope>> output = consumer.poll(ssps, 10);
    Map map = output.entrySet()
        .stream()
        .collect(Collectors.toMap(entry -> entry.getKey().getPartition().getPartitionId(), entry -> entry.getValue()
            .stream()
            .map(e -> (T) e.getMessage())
            .filter(e -> !(e instanceof EndOfStreamMessage))
            .collect(Collectors.toList())));
    return map;
  }

  public void containsInAnyOrder(List<? extends T> expected) throws InterruptedException {
    assertThat(consume(), IsIterableContainingInAnyOrder.containsInAnyOrder(expected.toArray()));
  }

  public void comparePartitionsInAnyOrder(List<? extends List> expected) throws InterruptedException {
    Map<Integer, List<T>> actual = consumePartitions();
    int i = 0;
    for(List parition: expected){
      assertThat(actual.get(i), IsIterableContainingInAnyOrder.containsInAnyOrder(parition.toArray()));
      i++;
    }
  }

  public void comparePartitionsInOrder(List<? extends List> expected) throws InterruptedException {
    Map<Integer, List<T>> actual = consumePartitions();
    int i = 0;
    for(List parition: expected){
      assertThat(actual.get(i), IsIterableContainingInOrder.contains(parition.toArray()));
      i++;
    }
  }

  public void contains(List<? extends T> expected) throws InterruptedException {
    assertThat(consume(), IsIterableContainingInOrder.contains(expected.toArray()));
  }

  public void size(Integer size) throws InterruptedException {
    assertThat(consume(), hasSize(size));
  }
}
