package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.RefundPolicyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RefundPolicyRuleRepository extends JpaRepository<RefundPolicyRule, Long> {

    List<RefundPolicyRule> findByPolicyIdOrderByMinHoursBeforeShowDesc(Long policyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefundPolicyRule r where r.policy.id = :policyId")
    int deleteByPolicyId(@Param("policyId") Long policyId);
}
