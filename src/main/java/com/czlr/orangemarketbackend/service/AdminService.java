package com.czlr.orangemarketbackend.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.enums.CommonStatus;
import com.czlr.orangemarketbackend.common.enums.OrderStatus;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.common.enums.OssUploadScene;
import com.czlr.orangemarketbackend.entity.dto.AdminProductImagesRequest;
import com.czlr.orangemarketbackend.entity.dto.AdminShipmentDTO;
import com.czlr.orangemarketbackend.entity.dto.AdminUserStatusDTO;
import com.czlr.orangemarketbackend.entity.dto.ProductDTO;
import com.czlr.orangemarketbackend.entity.dto.ShipOrderRequest;
import com.czlr.orangemarketbackend.entity.po.Order;
import com.czlr.orangemarketbackend.entity.po.Product;
import com.czlr.orangemarketbackend.entity.po.UserAccount;
import com.czlr.orangemarketbackend.mapper.OrderMapper;
import com.czlr.orangemarketbackend.mapper.ProductMapper;
import com.czlr.orangemarketbackend.mapper.UserAccountMapper;
import com.czlr.orangemarketbackend.service.oss.OssOwnedUrlValidator;
import com.czlr.orangemarketbackend.utils.AuthRedisKey;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AdminService {

    private static final int TRACKING_NO_MAX_LENGTH = 128;
    private static final int MAX_PRODUCT_IMAGES = 20;

    private final OrderMapper orderMapper;
    private final UserAccountMapper userAccountMapper;
    private final ProductMapper productMapper;
    private final ProductService productService;
    private final OssOwnedUrlValidator ossOwnedUrlValidator;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public AdminService(
            OrderMapper orderMapper,
            UserAccountMapper userAccountMapper,
            ProductMapper productMapper,
            ProductService productService,
            OssOwnedUrlValidator ossOwnedUrlValidator,
            ObjectMapper objectMapper,
            @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.orderMapper = orderMapper;
        this.userAccountMapper = userAccountMapper;
        this.productMapper = productMapper;
        this.productService = productService;
        this.ossOwnedUrlValidator = ossOwnedUrlValidator;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public AdminShipmentDTO shipOrder(Long orderId, ShipOrderRequest request) {
        validateId(orderId, "orderId");
        String trackingNo = requireTrackingNo(request);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() != OrderStatus.PENDING_SHIPMENT) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "仅待发货订单可以发货");
        }

        LocalDateTime shippedAt = LocalDateTime.now();
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.PENDING_SHIPMENT)
                .set(Order::getStatus, OrderStatus.PENDING_RECEIPT)
                .set(Order::getTrackingNo, trackingNo)
                .set(Order::getShippedAt, shippedAt));
        if (updated == 0) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "订单状态已发生变化，请刷新后重试");
        }

        return new AdminShipmentDTO(
                String.valueOf(order.getId()),
                order.getOrderNo(),
                OrderStatus.PENDING_RECEIPT,
                trackingNo,
                shippedAt);
    }

    @Transactional
    public AdminUserStatusDTO banUser(Long userId) {
        validateId(userId, "userId");

        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (isAdmin(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能封禁管理员账号");
        }

        if (user.getStatus() != CommonStatus.DISABLED) {
            int updated = userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                    .eq(UserAccount::getId, userId)
                    .eq(UserAccount::getStatus, CommonStatus.ACTIVE)
                    .set(UserAccount::getStatus, CommonStatus.DISABLED));
            if (updated == 0) {
                UserAccount latestUser = userAccountMapper.selectById(userId);
                if (latestUser == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
                }
                if (latestUser.getStatus() != CommonStatus.DISABLED) {
                    throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "用户状态已发生变化，请刷新后重试");
                }
                user = latestUser;
            } else {
                user.setStatus(CommonStatus.DISABLED);
            }
        }

        invalidateSessions(userId);
        return new AdminUserStatusDTO(String.valueOf(user.getId()), user.getStatus(), user.getRole());
    }

    @Transactional
    public ProductDTO updateProductImages(Long productId, AdminProductImagesRequest request) {
        validateId(productId, "productId");
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }

        List<String> images = normalizeProductImages(request);
        List<String> ownedImages = new ArrayList<>(images.size());
        for (String image : images) {
            ownedImages.add(ossOwnedUrlValidator.requireOwnedUrl(image, OssUploadScene.PRODUCT.getObjectPrefix()));
        }
        String coverImage = request.getCoverImage() == null || request.getCoverImage().isBlank()
                ? ownedImages.getFirst()
                : ossOwnedUrlValidator.requireOwnedUrl(
                        request.getCoverImage().trim(), OssUploadScene.PRODUCT.getObjectPrefix());

        product.setCoverImage(coverImage);
        product.setImagesJson(objectMapper.writeValueAsString(ownedImages));
        productMapper.updateById(product);
        productService.invalidateProductCaches(productId);
        return productService.toProductDTO(product);
    }

    private List<String> normalizeProductImages(AdminProductImagesRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品图片不能为空");
        }
        List<String> images = new ArrayList<>();
        if (request.getImages() != null) {
            for (String image : request.getImages()) {
                if (image != null && !image.isBlank()) {
                    images.add(image.trim());
                }
            }
        }
        if (images.isEmpty() && request.getCoverImage() != null && !request.getCoverImage().isBlank()) {
            images.add(request.getCoverImage().trim());
        }
        if (images.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品图片不能为空");
        }
        if (images.size() > MAX_PRODUCT_IMAGES) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品图片数量不能超过 20 张");
        }
        return images;
    }

    private String requireTrackingNo(ShipOrderRequest request) {
        if (request == null || request.getTrackingNo() == null || request.getTrackingNo().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "trackingNo 不能为空");
        }
        String trackingNo = request.getTrackingNo().trim();
        if (trackingNo.length() > TRACKING_NO_MAX_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "trackingNo 长度不能超过 128 个字符");
        }
        return trackingNo;
    }

    private void validateId(Long id, String parameterName) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, parameterName + " 参数错误");
        }
    }

    private boolean isAdmin(String role) {
        return role != null && "admin".equalsIgnoreCase(role.trim());
    }

    private void invalidateSessions(Long userId) {
        Set<String> candidateKeys = redisTemplate.keys(AuthRedisKey.loginPattern(userId));
        if (candidateKeys == null || candidateKeys.isEmpty()) {
            return;
        }

        Set<String> userSessionKeys = new HashSet<>();
        for (String key : candidateKeys) {
            Object sessionUserId = redisTemplate.opsForHash().get(key, "id");
            if (sessionUserId != null && userId.toString().equals(sessionUserId.toString())) {
                userSessionKeys.add(key);
            }
        }
        if (!userSessionKeys.isEmpty()) {
            redisTemplate.delete(userSessionKeys);
        }
    }
}
