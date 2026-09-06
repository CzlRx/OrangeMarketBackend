package com.czlr.orangemarketbackend.controller;

import com.czlr.orangemarketbackend.common.Result;
import com.czlr.orangemarketbackend.entity.dto.AddressDTO;
import com.czlr.orangemarketbackend.entity.dto.AddressRequest;
import com.czlr.orangemarketbackend.entity.dto.BrowseHistoryDTO;
import com.czlr.orangemarketbackend.entity.dto.BrowseHistoryPageDTO;
import com.czlr.orangemarketbackend.entity.dto.BrowseHistoryRequest;
import com.czlr.orangemarketbackend.entity.dto.FavoriteDTO;
import com.czlr.orangemarketbackend.entity.dto.FavoritePageDTO;
import com.czlr.orangemarketbackend.entity.dto.FavoriteRequest;
import com.czlr.orangemarketbackend.entity.dto.IdBatchRequest;
import com.czlr.orangemarketbackend.entity.dto.SearchHistoryDTO;
import com.czlr.orangemarketbackend.entity.dto.SearchHistoryPageDTO;
import com.czlr.orangemarketbackend.entity.dto.SearchHistoryRequest;
import com.czlr.orangemarketbackend.entity.dto.UserProfileDTO;
import com.czlr.orangemarketbackend.entity.dto.UserProfileUpdateRequest;
import com.czlr.orangemarketbackend.service.UserProfileService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping
    public Result<UserProfileDTO> getProfile(@RequestAttribute("userId") Long userId) {
        return Result.success(userProfileService.getProfile(userId));
    }

    @PatchMapping
    public Result<UserProfileDTO> updateProfile(
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("sessionId") String sessionId,
            @RequestBody UserProfileUpdateRequest request) {
        return Result.success(userProfileService.updateProfile(userId, sessionId, request));
    }

    @GetMapping("/addresses")
    public Result<List<AddressDTO>> getAddresses(@RequestAttribute("userId") Long userId) {
        return Result.success(userProfileService.getAddresses(userId));
    }

    @PostMapping("/addresses")
    public Result<AddressDTO> addAddress(
            @RequestAttribute("userId") Long userId,
            @RequestBody AddressRequest request) {
        return Result.success(userProfileService.addAddress(userId, request));
    }

    @PutMapping("/addresses/{addressId}")
    public Result<AddressDTO> updateAddress(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long addressId,
            @RequestBody AddressRequest request) {
        return Result.success(userProfileService.updateAddress(userId, addressId, request));
    }

    @DeleteMapping("/addresses/{addressId}")
    public Result<Void> deleteAddress(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long addressId) {
        userProfileService.deleteAddress(userId, addressId);
        return Result.success();
    }

    @PutMapping("/addresses/{addressId}/default")
    public Result<AddressDTO> setDefaultAddress(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long addressId) {
        return Result.success(userProfileService.setDefaultAddress(userId, addressId));
    }

    @GetMapping("/favorites")
    public Result<FavoritePageDTO> getFavorites(
            @RequestAttribute("userId") Long userId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.success(userProfileService.getFavorites(userId, page, pageSize));
    }

    @PostMapping("/favorites")
    public Result<FavoriteDTO> addFavorite(
            @RequestAttribute("userId") Long userId,
            @RequestBody FavoriteRequest request) {
        return Result.success(userProfileService.addFavorite(
                userId, request == null ? null : request.getProductId()));
    }

    @DeleteMapping("/favorites/{productId}")
    public Result<Void> removeFavorite(
            @RequestAttribute("userId") Long userId,
            @PathVariable String productId) {
        userProfileService.removeFavorite(userId, productId);
        return Result.success();
    }

    @GetMapping("/browse-history")
    public Result<BrowseHistoryPageDTO> getBrowseHistory(
            @RequestAttribute("userId") Long userId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.success(userProfileService.getBrowseHistory(userId, page, pageSize));
    }

    @PostMapping("/browse-history")
    public Result<BrowseHistoryDTO> recordBrowseHistory(
            @RequestAttribute("userId") Long userId,
            @RequestBody BrowseHistoryRequest request) {
        return Result.success(userProfileService.recordBrowseHistory(userId, request));
    }

    @DeleteMapping("/browse-history/{historyId}")
    public Result<Void> deleteBrowseHistory(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long historyId) {
        userProfileService.deleteBrowseHistory(userId, historyId);
        return Result.success();
    }

    @DeleteMapping("/browse-history")
    public Result<Void> clearBrowseHistory(@RequestAttribute("userId") Long userId) {
        userProfileService.clearBrowseHistory(userId);
        return Result.success();
    }

    @PostMapping("/browse-history/batch-delete")
    public Result<Void> batchDeleteBrowseHistory(
            @RequestAttribute("userId") Long userId,
            @RequestBody IdBatchRequest request) {
        userProfileService.batchDeleteBrowseHistory(userId, request);
        return Result.success();
    }

    @GetMapping("/search-history")
    public Result<SearchHistoryPageDTO> getSearchHistory(
            @RequestAttribute("userId") Long userId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        return Result.success(userProfileService.getSearchHistory(userId, page, pageSize));
    }

    @PostMapping("/search-history")
    public Result<SearchHistoryDTO> recordSearchHistory(
            @RequestAttribute("userId") Long userId,
            @RequestBody SearchHistoryRequest request) {
        return Result.success(userProfileService.recordSearchHistory(userId, request));
    }

    @DeleteMapping("/search-history/{historyId}")
    public Result<Void> deleteSearchHistory(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long historyId) {
        userProfileService.deleteSearchHistory(userId, historyId);
        return Result.success();
    }

    @DeleteMapping("/search-history")
    public Result<Void> clearSearchHistory(@RequestAttribute("userId") Long userId) {
        userProfileService.clearSearchHistory(userId);
        return Result.success();
    }
}
