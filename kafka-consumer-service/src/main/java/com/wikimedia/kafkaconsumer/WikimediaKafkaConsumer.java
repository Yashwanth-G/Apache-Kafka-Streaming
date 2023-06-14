package com.wikimedia.kafkaconsumer;

import com.wikimedia.entity.WikimediaData;
import com.wikimedia.repository.WikimediaDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class WikimediaKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(WikimediaKafkaConsumer.class);

    @Value("${spring.topic.name}")
    private String topicName;

    private WikimediaDataRepository repository;

    public WikimediaKafkaConsumer(WikimediaDataRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "${spring.topic.name}", groupId = "{spring.kafka.consumer.group-id}")
    public void consume(String message){
        logger.info(String.format("Event message received -> %s",message));
        WikimediaData data = new WikimediaData();
        data.setWikiEventData(message);
        repository.save(data);
    }
}
