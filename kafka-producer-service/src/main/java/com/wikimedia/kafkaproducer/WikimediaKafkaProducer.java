package com.wikimedia.kafkaproducer;

import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.background.BackgroundEventHandler;
import com.launchdarkly.eventsource.background.BackgroundEventSource;
import com.wikimedia.event.WikimediaChangeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@Service
public class WikimediaKafkaProducer {

    private static final Logger logger = LoggerFactory.getLogger(WikimediaKafkaProducer.class);

    @Value("${spring.topic.name}")
    private String topicName;

    @Value("${wikimedia_live_stream_data_URI}")
    private String liveStreamURI;

    private KafkaTemplate<String, String> template;

    // Single Constructor - no @Autowired annotation required
    public WikimediaKafkaProducer(KafkaTemplate<String, String> template){
        this.template = template;
    }

    public void sendMessage(){
        // To read realtime Stream Data from Wikimedia we use EventSource
        BackgroundEventHandler backgroundEventHandler = new WikimediaChangeHandler(template, topicName);
        EventSource.Builder event = new EventSource.Builder(URI.create(liveStreamURI));
        BackgroundEventSource.Builder builder = new BackgroundEventSource.Builder(backgroundEventHandler, event);
        BackgroundEventSource eventSource = builder.build();
        eventSource.start();
        try {
            TimeUnit.MINUTES.sleep(10);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
