package com.shotabra.repository;

import java.util.List;

import com.shotabra.domain.Word;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WordRepository extends JpaRepository<Word, Long> {

    List<Word> findByActiveTrue();

    long countByActiveTrue();
}
