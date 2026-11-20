package com.shotabra.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "word")
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String text;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false)
    private boolean active = true;

    protected Word() {
    }

    public Word(String text, String category) {
        this.text = text;
        this.category = category;
    }

    public Long getId() { return id; }
    public String getText() { return text; }
    public String getCategory() { return category; }
    public boolean isActive() { return active; }
}
