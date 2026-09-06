package com.czlr.orangemarketbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.entity.dto.AddressDTO;
import com.czlr.orangemarketbackend.entity.dto.AddressRequest;
import com.czlr.orangemarketbackend.entity.dto.BrowseHistoryDTO;
import com.czlr.orangemarketbackend.entity.dto.BrowseHistoryPageDTO;
import com.czlr.orangemarketbackend.entity.dto.BrowseHistoryRequest;
import com.czlr.orangemarketbackend.entity.dto.FavoriteDTO;
import com.czlr.orangemarketbackend.entity.dto.FavoritePageDTO;
import com.czlr.orangemarketbackend.entity.dto.IdBatchRequest;
import com.czlr.orangemarketbackend.entity.dto.SearchHistoryDTO;
import com.czlr.orangemarketbackend.entity.dto.SearchHistoryPageDTO;
import com.czlr.orangemarketbackend.entity.dto.SearchHistoryRequest;
import com.czlr.orangemarketbackend.entity.dto.UserProfileDTO;
import com.czlr.orangemarketbackend.entity.dto.UserProfileUpdateRequest;
import com.czlr.orangemarketbackend.entity.po.Product;
import com.czlr.orangemarketbackend.entity.po.UserAccount;
import com.czlr.orangemarketbackend.entity.po.UserAddress;
import com.czlr.orangemarketbackend.entity.po.UserBrowseHistory;
import com.czlr.orangemarketbackend.entity.po.UserFavorite;
import com.czlr.orangemarketbackend.entity.po.UserSearchHistory;
import com.czlr.orangemarketbackend.mapper.UserAccountMapper;
import com.czlr.orangemarketbackend.mapper.UserAddressMapper;
import com.czlr.orangemarketbackend.mapper.UserBrowseHistoryMapper;
import com.czlr.orangemarketbackend.mapper.UserFavoriteMapper;
import com.czlr.orangemarketbackend.mapper.UserSearchHistoryMapper;
import com.czlr.orangemarketbackend.mapper.ProductMapper;
import com.czlr.orangemarketbackend.utils.AuthRedisKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserProfileService {

    private static final int DEFAULT_VALUE = 0;
    private static final int TRUE_VALUE = 1;
    private static final int MAX_PAGE_SIZE = 50;
    private static final String PHONE_PATTERN = "1\\d{10}";

    private final UserAccountMapper userAccountMapper;
    private final UserAddressMapper userAddressMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final UserBrowseHistoryMapper userBrowseHistoryMapper;
    private final UserSearchHistoryMapper userSearchHistoryMapper;
    private final ProductMapper productMapper;
    private final ProductService productService;
    private final RedisTemplate<String, Object> redisTemplate;

    public UserProfileService(
            UserAddressMapper userAddressMapper,
            UserAccountMapper userAccountMapper,
            UserFavoriteMapper userFavoriteMapper,
            UserBrowseHistoryMapper userBrowseHistoryMapper,
            UserSearchHistoryMapper userSearchHistoryMapper,
            ProductMapper productMapper,
            ProductService productService,
            RedisTemplate<String, Object> redisTemplate) {
        this.userAccountMapper = userAccountMapper;
        this.userAddressMapper = userAddressMapper;
        this.userFavoriteMapper = userFavoriteMapper;
        this.userBrowseHistoryMapper = userBrowseHistoryMapper;
        this.userSearchHistoryMapper = userSearchHistoryMapper;
        this.productMapper = productMapper;
        this.productService = productService;
        this.redisTemplate = redisTemplate;
    }

    public UserProfileDTO getProfile(Long userId) {
        return toUserProfile(getUser(userId));
    }

    public UserProfileDTO updateProfile(
            Long userId, String sessionId, UserProfileUpdateRequest request) {
        if (request == null
                || (request.getNickname() == null
                && request.getGender() == null
                && request.getBirthday() == null)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "至少提供 nickname、gender 或 birthday 字段");
        }

        UserAccount user = getUser(userId);
        if (request.getNickname() != null) {
            user.setNickname(requireText(request.getNickname(), "nickname"));
        }
        if (request.getGender() != null) {
            if (request.getGender() < 0 || request.getGender() > 2) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "gender 参数错误");
            }
            user.setGender(request.getGender());
        }
        if (request.getBirthday() != null) {
            user.setBirthday(request.getBirthday());
        }

        userAccountMapper.updateById(user);
        syncSession(user, sessionId);
        return toUserProfile(user);
    }

    public List<AddressDTO> getAddresses(Long userId) {
        return userAddressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .orderByDesc(UserAddress::getCreatedAt)
                        .orderByDesc(UserAddress::getId))
                .stream()
                .map(this::toAddressDTO)
                .toList();
    }

    @Transactional
    public AddressDTO addAddress(Long userId, AddressRequest request) {
        validateAddressRequest(request);
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        applyAddressRequest(address, request);
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearOtherDefaults(userId);
            address.setIsDefault(TRUE_VALUE);
        }
        userAddressMapper.insert(address);
        return toAddressDTO(address);
    }

    @Transactional
    public AddressDTO updateAddress(Long userId, Long addressId, AddressRequest request) {
        validateAddressRequest(request);
        UserAddress address = getOwnedAddress(userId, addressId);
        applyAddressRequest(address, request);

        if (request.getIsDefault() != null) {
            if (request.getIsDefault()) {
                clearOtherDefaults(userId);
            }
            address.setIsDefault(request.getIsDefault() ? TRUE_VALUE : DEFAULT_VALUE);
        }
        userAddressMapper.updateById(address);
        return toAddressDTO(address);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        UserAddress address = getOwnedAddress(userId, addressId);
        boolean wasDefault = isDefault(address);
        if (userAddressMapper.deleteById(address.getId()) == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "地址不存在");
        }

        if (wasDefault) {
            clearOtherDefaults(userId);
            UserAddress fallback = findLatestAddress(userId);
            if (fallback != null) {
                fallback.setIsDefault(TRUE_VALUE);
                userAddressMapper.updateById(fallback);
            }
        }
    }

    @Transactional
    public AddressDTO setDefaultAddress(Long userId, Long addressId) {
        UserAddress address = getOwnedAddress(userId, addressId);
        clearOtherDefaults(userId);
        address.setIsDefault(TRUE_VALUE);
        userAddressMapper.updateById(address);
        return toAddressDTO(address);
    }

    public FavoritePageDTO getFavorites(Long userId, int page, int pageSize) {
        validatePage(page, pageSize);
        Page<UserFavorite> favoritePage = userFavoriteMapper.selectPage(
                new Page<>(page, pageSize), new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getCreatedAt)
                        .orderByDesc(UserFavorite::getId));
        Map<Long, Product> products = getProducts(favoritePage.getRecords().stream()
                .map(UserFavorite::getProductId)
                .toList());
        List<FavoriteDTO> favorites = favoritePage.getRecords().stream()
                .map(favorite -> toFavoriteDTO(favorite, products.get(favorite.getProductId())))
                .toList();
        return new FavoritePageDTO(
                favorites,
                Math.toIntExact(favoritePage.getTotal()),
                (int) favoritePage.getCurrent(),
                (int) favoritePage.getSize(),
                favoritePage.hasNext());
    }

    @Transactional
    public FavoriteDTO addFavorite(Long userId, String productId) {
        Long parsedProductId = parseId(productId, "productId");
        Product product = getExistingProduct(parsedProductId);
        UserFavorite favorite = userFavoriteMapper.selectOne(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getProductId, parsedProductId));
        if (favorite == null) {
            favorite = new UserFavorite();
            favorite.setUserId(userId);
            favorite.setProductId(parsedProductId);
            userFavoriteMapper.insert(favorite);
        }
        return toFavoriteDTO(favorite, product);
    }

    @Transactional
    public void removeFavorite(Long userId, String productId) {
        Long parsedProductId = parseId(productId, "productId");
        userFavoriteMapper.delete(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getProductId, parsedProductId));
    }

    public BrowseHistoryPageDTO getBrowseHistory(Long userId, int page, int pageSize) {
        validatePage(page, pageSize);
        Page<UserBrowseHistory> historyPage = userBrowseHistoryMapper.selectPage(
                new Page<>(page, pageSize), new LambdaQueryWrapper<UserBrowseHistory>()
                        .eq(UserBrowseHistory::getUserId, userId)
                        .orderByDesc(UserBrowseHistory::getViewedAt)
                        .orderByDesc(UserBrowseHistory::getId));
        Map<Long, Product> products = getProducts(historyPage.getRecords().stream()
                .map(UserBrowseHistory::getProductId)
                .toList());
        List<BrowseHistoryDTO> histories = historyPage.getRecords().stream()
                .map(history -> toBrowseHistoryDTO(history, products.get(history.getProductId())))
                .toList();
        return new BrowseHistoryPageDTO(
                histories,
                Math.toIntExact(historyPage.getTotal()),
                (int) historyPage.getCurrent(),
                (int) historyPage.getSize(),
                historyPage.hasNext());
    }

    @Transactional
    public BrowseHistoryDTO recordBrowseHistory(Long userId, BrowseHistoryRequest request) {
        Long productId = parseId(request == null ? null : request.getProductId(), "productId");
        Product product = getExistingProduct(productId);
        UserBrowseHistory history = userBrowseHistoryMapper.selectOne(new LambdaQueryWrapper<UserBrowseHistory>()
                .eq(UserBrowseHistory::getUserId, userId)
                .eq(UserBrowseHistory::getProductId, productId));
        if (history == null) {
            history = new UserBrowseHistory();
            history.setUserId(userId);
            history.setProductId(productId);
            history.setViewedAt(LocalDateTime.now());
            userBrowseHistoryMapper.insert(history);
        } else {
            history.setViewedAt(LocalDateTime.now());
            userBrowseHistoryMapper.updateById(history);
        }
        return toBrowseHistoryDTO(history, product);
    }

    @Transactional
    public void deleteBrowseHistory(Long userId, Long historyId) {
        int deleted = userBrowseHistoryMapper.delete(new LambdaQueryWrapper<UserBrowseHistory>()
                .eq(UserBrowseHistory::getId, historyId)
                .eq(UserBrowseHistory::getUserId, userId));
        if (deleted == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "浏览足迹不存在");
        }
    }

    @Transactional
    public void clearBrowseHistory(Long userId) {
        userBrowseHistoryMapper.delete(new LambdaQueryWrapper<UserBrowseHistory>()
                .eq(UserBrowseHistory::getUserId, userId));
    }

    @Transactional
    public void batchDeleteBrowseHistory(Long userId, IdBatchRequest request) {
        if (request == null || request.getIds() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "ids 字段不能为空");
        }
        List<Long> ids = request.getIds().stream()
                .map(id -> parseId(id, "ids"))
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        userBrowseHistoryMapper.delete(new LambdaQueryWrapper<UserBrowseHistory>()
                .eq(UserBrowseHistory::getUserId, userId)
                .in(UserBrowseHistory::getId, ids));
    }

    public SearchHistoryPageDTO getSearchHistory(Long userId, int page, int pageSize) {
        validatePage(page, pageSize);
        Page<UserSearchHistory> historyPage = userSearchHistoryMapper.selectPage(
                new Page<>(page, pageSize), new LambdaQueryWrapper<UserSearchHistory>()
                        .eq(UserSearchHistory::getUserId, userId)
                        .orderByDesc(UserSearchHistory::getSearchedAt)
                        .orderByDesc(UserSearchHistory::getId));
        List<SearchHistoryDTO> histories = historyPage.getRecords().stream()
                .map(this::toSearchHistoryDTO)
                .toList();
        return new SearchHistoryPageDTO(
                histories,
                Math.toIntExact(historyPage.getTotal()),
                (int) historyPage.getCurrent(),
                (int) historyPage.getSize(),
                historyPage.hasNext());
    }

    @Transactional
    public SearchHistoryDTO recordSearchHistory(Long userId, SearchHistoryRequest request) {
        String keyword = requireText(request == null ? null : request.getKeyword(), "keyword");
        UserSearchHistory history = userSearchHistoryMapper.selectOne(new LambdaQueryWrapper<UserSearchHistory>()
                .eq(UserSearchHistory::getUserId, userId)
                .eq(UserSearchHistory::getKeyword, keyword));
        if (history == null) {
            history = new UserSearchHistory();
            history.setUserId(userId);
            history.setKeyword(keyword);
            history.setSearchedAt(LocalDateTime.now());
            userSearchHistoryMapper.insert(history);
        } else {
            history.setSearchedAt(LocalDateTime.now());
            userSearchHistoryMapper.updateById(history);
        }
        return toSearchHistoryDTO(history);
    }

    @Transactional
    public void deleteSearchHistory(Long userId, Long historyId) {
        int deleted = userSearchHistoryMapper.delete(new LambdaQueryWrapper<UserSearchHistory>()
                .eq(UserSearchHistory::getId, historyId)
                .eq(UserSearchHistory::getUserId, userId));
        if (deleted == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "搜索历史不存在");
        }
    }

    @Transactional
    public void clearSearchHistory(Long userId) {
        userSearchHistoryMapper.delete(new LambdaQueryWrapper<UserSearchHistory>()
                .eq(UserSearchHistory::getUserId, userId));
    }

    private UserAccount getUser(Long userId) {
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private UserProfileDTO toUserProfile(UserAccount user) {
        return new UserProfileDTO(
                toString(user.getId()),
                user.getPhone(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getGender(),
                user.getBirthday(),
                user.getStatus(),
                user.getRole(),
                user.getLastLoginAt());
    }

    private void syncSession(UserAccount user, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            String redisKey = AuthRedisKey.login(user.getId(), sessionId);
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
                return;
            }
            redisTemplate.opsForHash().put(redisKey, "nickname", user.getNickname());
            redisTemplate.opsForHash().put(redisKey, "gender", user.getGender());
            if (user.getBirthday() == null) {
                redisTemplate.opsForHash().delete(redisKey, "birthday");
            } else {
                redisTemplate.opsForHash().put(redisKey, "birthday", user.getBirthday().toString());
            }
        } catch (RuntimeException ignored) {
        }
    }

    private AddressDTO toAddressDTO(UserAddress address) {
        return new AddressDTO(
                toString(address.getId()),
                address.getReceiver(),
                maskPhone(address.getPhone()),
                address.getProvinceCode(),
                address.getProvince(),
                address.getCityCode(),
                address.getCity(),
                address.getDistrictCode(),
                address.getDistrict(),
                address.getDetail(),
                isDefault(address));
    }

    private void validateAddressRequest(AddressRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "地址不能为空");
        }
        requireText(request.getReceiver(), "receiver");
        validatePhone(request.getPhone());
        requireText(request.getProvince(), "province");
        requireText(request.getCity(), "city");
        requireText(request.getDistrict(), "district");
        requireText(request.getDetail(), "detail");
    }

    private void applyAddressRequest(UserAddress address, AddressRequest request) {
        address.setReceiver(request.getReceiver().trim());
        address.setPhone(request.getPhone().trim());
        address.setProvinceCode(normalizeOptional(request.getProvinceCode()));
        address.setProvince(request.getProvince().trim());
        address.setCityCode(normalizeOptional(request.getCityCode()));
        address.setCity(request.getCity().trim());
        address.setDistrictCode(normalizeOptional(request.getDistrictCode()));
        address.setDistrict(request.getDistrict().trim());
        address.setDetail(request.getDetail().trim());
        if (address.getIsDefault() == null) {
            address.setIsDefault(DEFAULT_VALUE);
        }
    }

    private UserAddress getOwnedAddress(Long userId, Long addressId) {
        UserAddress address = userAddressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, addressId)
                .eq(UserAddress::getUserId, userId));
        if (address == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "地址不存在");
        }
        return address;
    }

    private UserAddress findLatestAddress(Long userId) {
        return userAddressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .orderByDesc(UserAddress::getCreatedAt)
                        .orderByDesc(UserAddress::getId))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void clearOtherDefaults(Long userId) {
        userAddressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, TRUE_VALUE)
                .set(UserAddress::getIsDefault, DEFAULT_VALUE));
    }

    private FavoriteDTO toFavoriteDTO(UserFavorite favorite, Product product) {
        return new FavoriteDTO(
                toString(favorite.getId()),
                toString(favorite.getProductId()),
                favorite.getCreatedAt(),
                product == null ? null : productService.toProductDTO(product));
    }

    private BrowseHistoryDTO toBrowseHistoryDTO(UserBrowseHistory history, Product product) {
        return new BrowseHistoryDTO(
                toString(history.getId()),
                toString(history.getProductId()),
                history.getViewedAt(),
                product == null ? null : productService.toProductDTO(product));
    }

    private SearchHistoryDTO toSearchHistoryDTO(UserSearchHistory history) {
        return new SearchHistoryDTO(
                toString(history.getId()), history.getKeyword(), history.getSearchedAt());
    }

    private Map<Long, Product> getProducts(List<Long> productIds) {
        List<Long> ids = productIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return productMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Product::getId, product -> product, (first, second) -> first,
                        LinkedHashMap::new));
    }

    private Product getExistingProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        return product;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页参数错误");
        }
    }

    private Long parseId(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, parameterName + " 不能为空");
        }
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, parameterName + " 参数错误");
        }
    }

    private String requireText(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, parameterName + " 不能为空");
        }
        return value.trim();
    }

    private void validatePhone(String phone) {
        if (phone == null || !phone.trim().matches(PHONE_PATTERN)) {
            throw new BusinessException(ResultCode.PHONE_INVALID, "手机号格式错误");
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isDefault(UserAddress address) {
        return Integer.valueOf(TRUE_VALUE).equals(address.getIsDefault());
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String toString(Long value) {
        return value == null ? null : value.toString();
    }
}
