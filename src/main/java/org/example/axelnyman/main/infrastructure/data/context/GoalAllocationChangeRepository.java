package org.example.axelnyman.main.infrastructure.data.context;

import org.example.axelnyman.main.domain.model.GoalAllocationChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GoalAllocationChangeRepository extends JpaRepository<GoalAllocationChange, UUID> {

    List<GoalAllocationChange> findAllBySavingsGoalIdOrderByCreatedAtDesc(UUID savingsGoalId);
}
