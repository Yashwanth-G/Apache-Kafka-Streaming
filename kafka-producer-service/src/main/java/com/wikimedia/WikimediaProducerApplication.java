package com.wikimedia;

import com.wikimedia.kafkaproducer.WikimediaKafkaProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WikimediaProducerApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(WikimediaProducerApplication.class);
    }

    @Autowired
    private WikimediaKafkaProducer producer;

    /**
     * Callback used to run the bean.
     *
     * @param args incoming main method arguments
     * @throws Exception on error
     */
    @Override
    public void run(String... args) throws Exception {
        producer.sendMessage();
    }
}
