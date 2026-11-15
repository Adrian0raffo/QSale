package com.example.qsale.vote.infrastructure;

import com.example.qsale.vote.domain.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    boolean existsByOptionIdAndUserId(Long optionId, Long userId);

    Optional<Vote> findByOptionIdAndUserId(Long optionId, Long userId);

    long countByOptionId(Long optionId);

    long deleteByOption_Plan_IdAndUser_Id(Long planId, Long userId);

    @Query("select v.option.id from Vote v where v.user.id = :userId and v.option.id in :optionIds")
    List<Long> findVotedOptionIds(@Param("userId") Long userId, @Param("optionIds") List<Long> optionIds);
}
