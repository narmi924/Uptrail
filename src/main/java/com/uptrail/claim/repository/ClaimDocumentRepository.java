package com.uptrail.claim.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uptrail.claim.domain.ClaimDocument;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, Long> {
}
