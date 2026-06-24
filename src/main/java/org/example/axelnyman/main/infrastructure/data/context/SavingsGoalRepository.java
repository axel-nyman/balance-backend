package org.example.axelnyman.main.infrastructure.data.context;

import org.example.axelnyman.main.domain.model.GoalStatus;
import org.example.axelnyman.main.domain.model.SavingsGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {

    List<SavingsGoal> findAllByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(GoalStatus status);
}
