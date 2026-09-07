package com.czlr.orangemarketbackend.controller;

import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.entity.dto.PendingReviewItemDTO;
import com.czlr.orangemarketbackend.entity.dto.ReviewSubmissionResultDTO;
import com.czlr.orangemarketbackend.entity.dto.SubmitReviewsRequest;
import com.czlr.orangemarketbackend.service.ReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/users/me/reviews/pending")
    public Result<List<PendingReviewItemDTO>> getPendingReviews(
            @RequestAttribute("userId") Long userId) {
        return Result.success(reviewService.getPendingReviews(userId));
    }

    @PostMapping("/orders/{orderId}/reviews")
    public Result<ReviewSubmissionResultDTO> submitReviews(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long orderId,
            @RequestBody SubmitReviewsRequest request) {
        return Result.success(reviewService.submitReviews(userId, orderId, request));
    }
}
