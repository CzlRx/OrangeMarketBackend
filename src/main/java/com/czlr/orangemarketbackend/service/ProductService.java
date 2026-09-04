package com.czlr.orangemarketbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.enums.ProductStatus;
import com.czlr.orangemarketbackend.common.enums.ReviewStatus;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.entity.dto.ProductDTO;
import com.czlr.orangemarketbackend.entity.dto.ProductDetailDTO;
import com.czlr.orangemarketbackend.entity.dto.ProductPageDTO;
import com.czlr.orangemarketbackend.entity.dto.ProductReviewPageDTO;
import com.czlr.orangemarketbackend.entity.dto.ReviewDTO;
import com.czlr.orangemarketbackend.entity.dto.ReviewSummaryDTO;
import com.czlr.orangemarketbackend.entity.po.Product;
import com.czlr.orangemarketbackend.entity.po.ProductReview;
import com.czlr.orangemarketbackend.entity.po.UserAccount;
import com.czlr.orangemarketbackend.mapper.ProductMapper;
import com.czlr.orangemarketbackend.mapper.ProductReviewMapper;
import com.czlr.orangemarketbackend.mapper.UserAccountMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

@Service
public class ProductService extends ServiceImpl<ProductMapper, Product> {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DETAIL_REVIEW_SIZE = 3;
    private static final Set<String> PRODUCT_SORTS = Set.of("default", "price_asc", "sales_desc");
    private static final Set<String> REVIEW_FILTERS = Set.of("all", "good", "medium", "bad", "media");
    private static final String PRODUCT_LIST_CACHE_PREFIX = "cache:products:list:v1:";
    private static final String PRODUCT_DETAIL_CACHE_PREFIX = "cache:products:detail:v1:";
    private static final String PRODUCT_REVIEWS_CACHE_PREFIX = "cache:products:reviews:v1:";
    private static final long PRODUCT_LIST_CACHE_TTL_SECONDS = 300;
    private static final long PRODUCT_DETAIL_CACHE_TTL_SECONDS = 300;
    private static final long PRODUCT_REVIEWS_CACHE_TTL_SECONDS = 120;

    private final ProductReviewMapper productReviewMapper;
    private final UserAccountMapper userAccountMapper;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public ProductService(
            ProductReviewMapper productReviewMapper,
            UserAccountMapper userAccountMapper,
            ObjectMapper objectMapper,
            @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.productReviewMapper = productReviewMapper;
        this.userAccountMapper = userAccountMapper;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    public Page<ProductDTO> getProducts(Page<ProductDTO> page, String keyword, String categoryId, String sort) {
        validatePage(page.getCurrent(), page.getSize(), MAX_PAGE_SIZE);
        if (!PRODUCT_SORTS.contains(sort)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的商品排序方式");
        }

        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        Long normalizedCategoryId = categoryId == null || categoryId.isBlank()
                ? null
                : parseId(categoryId.trim(), "categoryId");
        String cacheKey = productListCacheKey(
                page.getCurrent(), page.getSize(), normalizedKeyword, normalizedCategoryId, sort);
        ProductPageDTO cachedPage = readCache(cacheKey, ProductPageDTO.class);
        if (cachedPage != null) {
            return toProductPage(cachedPage);
        }

        LambdaQueryWrapper<Product> query = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, ProductStatus.ON_SALE);

        if (!normalizedKeyword.isBlank()) {
            query.and(wrapper -> wrapper
                    .like(Product::getName, normalizedKeyword)
                    .or()
                    .like(Product::getSubtitle, normalizedKeyword)
                    .or()
                    .like(Product::getDescription, normalizedKeyword));
        }
        if (normalizedCategoryId != null) {
            query.eq(Product::getCategoryId, normalizedCategoryId);
        }
        applyProductSort(query, sort);

        Page<Product> productPage = new Page<>(page.getCurrent(), page.getSize());
        productPage = baseMapper.selectPage(productPage, query);

        Page<ProductDTO> result = new Page<>(
                productPage.getCurrent(), productPage.getSize(), productPage.getTotal());
        result.setRecords(productPage.getRecords().stream().map(this::toProductDTO).toList());
        writeCache(cacheKey, toProductPageDTO(result), PRODUCT_LIST_CACHE_TTL_SECONDS);
        return result;
    }

    public ProductDetailDTO getProductDetail(Long productId) {
        String cacheKey = PRODUCT_DETAIL_CACHE_PREFIX + productId;
        ProductDetailDTO cachedDetail = readCache(cacheKey, ProductDetailDTO.class);
        if (cachedDetail != null) {
            return cachedDetail;
        }

        Product product = getVisibleProduct(productId);
        List<ProductReview> reviews = productReviewMapper.selectPage(
                new Page<>(1, DETAIL_REVIEW_SIZE), reviewQuery(productId, "all")).getRecords();
        Map<Long, UserAccount> users = getUsersById(reviews);
        ReviewSummaryDTO summary = buildReviewSummary(productId);
        ProductDetailDTO result = new ProductDetailDTO(
                toProductDTO(product),
                reviews.stream().map(review -> toReviewDTO(review, users)).toList(),
                summary);
        writeCache(cacheKey, result, PRODUCT_DETAIL_CACHE_TTL_SECONDS);
        return result;
    }

    public ProductReviewPageDTO getProductReviews(
            Long productId, String filter, int page, int pageSize, String sort) {
        validatePage(page, pageSize, MAX_PAGE_SIZE);
        if (!REVIEW_FILTERS.contains(filter)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的评价筛选条件");
        }
        if (!"latest".equals(sort)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前仅支持 latest 排序");
        }

        String cacheKey = PRODUCT_REVIEWS_CACHE_PREFIX
                + productId + ":" + filter + ":" + page + ":" + pageSize + ":" + sort;
        ProductReviewPageDTO cachedReviews = readCache(cacheKey, ProductReviewPageDTO.class);
        if (cachedReviews != null) {
            return cachedReviews;
        }

        getVisibleProduct(productId);
        Page<ProductReview> reviewPage = new Page<>(page, pageSize);
        if ("media".equals(filter)) {
            reviewPage.setRecords(Collections.emptyList());
            reviewPage.setTotal(0);
        } else {
            reviewPage = productReviewMapper.selectPage(reviewPage, reviewQuery(productId, filter));
        }

        Map<Long, UserAccount> users = getUsersById(reviewPage.getRecords());
        List<ReviewDTO> reviews = reviewPage.getRecords().stream()
                .map(review -> toReviewDTO(review, users))
                .toList();
        ProductReviewPageDTO result = new ProductReviewPageDTO(
                reviews,
                Math.toIntExact(reviewPage.getTotal()),
                (int) reviewPage.getCurrent(),
                (int) reviewPage.getSize(),
                reviewPage.hasNext(),
                buildReviewSummary(productId));
        writeCache(cacheKey, result, PRODUCT_REVIEWS_CACHE_TTL_SECONDS);
        return result;
    }

    private String productListCacheKey(
            long page, long pageSize, String keyword, Long categoryId, String sort) {
        return PRODUCT_LIST_CACHE_PREFIX
                + page + ":" + pageSize + ":" + keyword + ":" + categoryId + ":" + sort;
    }

    private <T> T readCache(String cacheKey, Class<T> targetType) {
        Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
        return cachedValue == null ? null : objectMapper.convertValue(cachedValue, targetType);
    }

    private void writeCache(String cacheKey, Object value, long ttlSeconds) {
        redisTemplate.opsForValue().set(cacheKey, value, ttlSeconds, TimeUnit.SECONDS);
    }

    private ProductPageDTO toProductPageDTO(Page<ProductDTO> page) {
        return new ProductPageDTO(
                page.getRecords(),
                Math.toIntExact(page.getTotal()),
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.hasNext());
    }

    private Page<ProductDTO> toProductPage(ProductPageDTO cachedPage) {
        Page<ProductDTO> page = new Page<>(cachedPage.getPage(), cachedPage.getPageSize(), cachedPage.getTotal());
        page.setRecords(cachedPage.getList() == null ? List.of() : cachedPage.getList());
        return page;
    }

    private LambdaQueryWrapper<ProductReview> reviewQuery(Long productId, String filter) {
        LambdaQueryWrapper<ProductReview> query = new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getProductId, productId)
                .eq(ProductReview::getStatus, ReviewStatus.VISIBLE)
                .orderByDesc(ProductReview::getCreatedAt)
                .orderByDesc(ProductReview::getId);
        switch (filter) {
            case "good" -> query.ge(ProductReview::getRating, 4);
            case "medium" -> query.eq(ProductReview::getRating, 3);
            case "bad" -> query.le(ProductReview::getRating, 2);
            case "all" -> { }
            case "media" -> query.eq(ProductReview::getId, -1L);
            default -> throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的评价筛选条件");
        }
        return query;
    }

    private ReviewSummaryDTO buildReviewSummary(Long productId) {
        List<ProductReview> reviews = productReviewMapper.selectList(
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getProductId, productId)
                        .eq(ProductReview::getStatus, ReviewStatus.VISIBLE));
        int allCount = reviews.size();
        int goodCount = (int) reviews.stream().filter(review -> review.getRating() != null && review.getRating() >= 4).count();
        int mediumCount = (int) reviews.stream().filter(review -> Integer.valueOf(3).equals(review.getRating())).count();
        int badCount = (int) reviews.stream().filter(review -> review.getRating() != null && review.getRating() <= 2).count();
        double average = allCount == 0
                ? 0.0
                : reviews.stream().mapToInt(ProductReview::getRating).average().orElse(0.0);
        double goodRate = allCount == 0 ? 0.0 : (double) goodCount / allCount;
        return new ReviewSummaryDTO(
                average, allCount, goodRate, allCount, goodCount, mediumCount, badCount);
    }

    private Map<Long, UserAccount> getUsersById(List<ProductReview> reviews) {
        Set<Long> userIds = reviews.stream()
                .map(ProductReview::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userAccountMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
    }

    private ReviewDTO toReviewDTO(ProductReview review, Map<Long, UserAccount> users) {
        boolean anonymous = review.getAnonymous() != null && review.getAnonymous() != 0;
        UserAccount user = users.get(review.getUserId());
        return new ReviewDTO(
                toString(review.getId()),
                toString(review.getProductId()),
                anonymous ? "匿名用户" : user == null ? null : user.getNickname(),
                anonymous || user == null ? null : user.getAvatarUrl(),
                review.getContent(),
                review.getRating(),
                anonymous,
                review.getCreatedAt());
    }

    private ProductDTO toProductDTO(Product product) {
        return new ProductDTO(
                toString(product.getId()),
                product.getName(),
                product.getSubtitle(),
                toString(product.getCategoryId()),
                parseStringList(product.getImagesJson(), product.getCoverImage()),
                product.getVideoUrl(),
                product.getSalePrice(),
                product.getOriginalPrice(),
                product.getStock(),
                product.getSalesCount(),
                product.getRatingAvg() == null ? null : product.getRatingAvg().doubleValue(),
                product.getReviewCount(),
                product.getDescription(),
                product.getShippingFee(),
                parseStringList(product.getTagsJson(), null));
    }

    private List<String> parseStringList(String json, String fallback) {
        if (json != null && !json.isBlank()) {
            try {
                List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() { });
                if (values != null && !values.isEmpty()) {
                    return values;
                }
            } catch (RuntimeException e) {
                throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "商品数据格式错误");
            }
        }
        return fallback == null ? List.of() : List.of(fallback);
    }

    private Product getVisibleProduct(Long productId) {
        Product product = baseMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getId, productId)
                .eq(Product::getStatus, ProductStatus.ON_SALE));
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        return product;
    }

    private void applyProductSort(LambdaQueryWrapper<Product> query, String sort) {
        switch (sort) {
            case "price_asc" -> query.orderByAsc(Product::getSalePrice).orderByAsc(Product::getId);
            case "sales_desc" -> query.orderByDesc(Product::getSalesCount).orderByAsc(Product::getId);
            case "default" -> query.orderByAsc(Product::getSortOrder).orderByAsc(Product::getId);
            default -> throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的商品排序方式");
        }
    }

    private void validatePage(long page, long pageSize, int maxPageSize) {
        if (page < 1 || pageSize < 1 || pageSize > maxPageSize) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页参数错误");
        }
    }

    private Long parseId(String value, String parameterName) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, parameterName + " 参数错误");
        }
    }

    private String toString(Long value) {
        return value == null ? null : value.toString();
    }
}
