package com.wikimedia.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name="wikimedia_recent_change")
@Data
public class WikimediaData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Lob
    private String wikiEventData;
}
