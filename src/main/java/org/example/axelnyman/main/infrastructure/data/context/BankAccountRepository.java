package org.example.axelnyman.main.infrastructure.data.context;

import org.example.axelnyman.main.domain.model.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    @Query("SELECT CASE WHEN COUNT(ba) > 0 THEN true ELSE false END FROM BankAccount ba WHERE ba.name = :name AND ba.deletedAt IS NULL")
    boolean existsByNameAndDeletedAtIsNull(@Param("name") String name);

    List<BankAccount> findAllByDeletedAtIsNull();
}
