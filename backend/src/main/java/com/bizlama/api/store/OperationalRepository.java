package com.bizlama.api.store;

import com.bizlama.api.data.bigquery.AnalyticsPublisher;
import com.bizlama.api.domain.ActivityEvent;
import com.bizlama.api.domain.Dish;
import com.bizlama.api.domain.Feedback;
import com.bizlama.api.domain.Ingredient;
import com.bizlama.api.domain.InventoryLot;
import com.bizlama.api.domain.InventorySummary;
import com.bizlama.api.domain.MenuCategory;
import com.bizlama.api.domain.Order;
import com.bizlama.api.domain.OrderListItem;
import com.bizlama.api.domain.OrderSummary;
import com.bizlama.api.domain.ReceiptImport;
import com.bizlama.api.domain.RecipeVersion;
import com.bizlama.api.domain.StockLot;
import com.bizlama.api.domain.StockMovement;
import com.bizlama.api.experiment.ExperimentResponse;
import com.bizlama.api.experiment.ExperimentStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OperationalRepository {

    private final JdbcClient jdbc;
    private final AnalyticsPublisher analytics;

    public OperationalRepository(
            JdbcClient jdbc,
            AnalyticsPublisher analytics) {

        this.jdbc = jdbc;
        this.analytics = analytics;
    }

    // ============================================================
    // INGREDIENTS
    // ============================================================

    public List<Ingredient> ingredients() {
        return jdbc.sql("""
                        SELECT id, name, base_unit, active
                        FROM ingredients
                        ORDER BY name
                        """)
                .query(this::ingredient)
                .list();
    }

    public Optional<Ingredient> ingredient(String id) {
        return jdbc.sql("""
                        SELECT id, name, base_unit, active
                        FROM ingredients
                        WHERE id = :id
                        """)
                .param("id", id)
                .query(this::ingredient)
                .optional();
    }

    public Ingredient saveIngredient(Ingredient value) {

        int updated = jdbc.sql("""
                        UPDATE ingredients
                        SET name = :name,
                            base_unit = :unit,
                            active = :active
                        WHERE id = :id
                        """)
                .params(Map.of(
                        "id", value.id(),
                        "name", value.name(),
                        "unit", value.baseUnit(),
                        "active", value.active()))
                .update();

        if (updated == 0) {
            jdbc.sql("""
                            INSERT INTO ingredients
                            (id, name, base_unit, active)
                            VALUES (:id, :name, :unit, :active)
                            """)
                    .params(Map.of(
                            "id", value.id(),
                            "name", value.name(),
                            "unit", value.baseUnit(),
                            "active", value.active()))
                    .update();
        }

        return value;
    }

    public void deleteIngredient(String id) {
        jdbc.sql("""
                        UPDATE ingredients
                        SET active = FALSE
                        WHERE id = :id
                        """)
                .param("id", id)
                .update();
    }

    // ============================================================
    // MENU CATEGORIES
    // ============================================================

    public List<MenuCategory> menuCategories() {
        return jdbc.sql("""
                        SELECT id, name
                        FROM catalog_categories
                        WHERE kitchen_id = 'kitchen-default'
                        ORDER BY name
                        """)
                .query((rs, rowNum) ->
                        new MenuCategory(
                                rs.getString("id"),
                                rs.getString("name")))
                .list();
    }

    public Optional<MenuCategory> menuCategory(String id) {
        return jdbc.sql("""
                        SELECT id, name
                        FROM catalog_categories
                        WHERE id = :id
                          AND kitchen_id = 'kitchen-default'
                        """)
                .param("id", id)
                .query((rs, rowNum) ->
                        new MenuCategory(
                                rs.getString("id"),
                                rs.getString("name")))
                .optional();
    }

    // ============================================================
    // DISHES
    // ============================================================

    public List<Dish> dishes() {
        return jdbc.sql("""
                        SELECT d.id,
                               d.name,
                               d.price,
                               d.active_recipe_version_id,
                               d.active,
                               d.category_id,
                               COALESCE(c.name, 'Other') AS category_name
                        FROM dishes d
                        LEFT JOIN catalog_categories c
                            ON c.id = d.category_id
                        WHERE d.active = TRUE
                        ORDER BY COALESCE(c.name, 'Other'), d.name
                        """)
                .query(this::dish)
                .list();
    }

    public Optional<Dish> dish(String id) {
        return jdbc.sql("""
                        SELECT d.id,
                               d.name,
                               d.price,
                               d.active_recipe_version_id,
                               d.active,
                               d.category_id,
                               COALESCE(c.name, 'Other') AS category_name
                        FROM dishes d
                        LEFT JOIN catalog_categories c
                            ON c.id = d.category_id
                        WHERE d.id = :id
                        """)
                .param("id", id)
                .query(this::dish)
                .optional();
    }

    public Dish saveDish(Dish value) {

        int updated = jdbc.sql("""
                        UPDATE dishes
                        SET name = :name,
                            price = :price,
                            active = :active,
                            category_id = :category
                        WHERE id = :id
                        """)
                .param("id", value.id())
                .param("name", value.name())
                .param("price", value.price())
                .param("active", value.active())
                .param("category", value.categoryId())
                .update();

        if (updated == 0) {
            jdbc.sql("""
                            INSERT INTO dishes
                            (id, name, price, active_recipe_version_id, active, category_id)
                            VALUES
                            (:id, :name, :price, :recipe, :active, :category)
                            """)
                    .param("id", value.id())
                    .param("name", value.name())
                    .param("price", value.price())
                    .param("recipe", value.activeRecipeVersionId())
                    .param("active", value.active())
                    .param("category", value.categoryId())
                    .update();
        }

        return dish(value.id()).orElse(value);
    }

    public void deleteDish(String id) {
        jdbc.sql("""
                        UPDATE dishes
                        SET active = FALSE
                        WHERE id = :id
                        """)
                .param("id", id)
                .update();
    }

    // ============================================================
    // RECIPES
    // ============================================================

    public List<RecipeVersion> recipes() {
        return jdbc.sql("""
                        SELECT id
                        FROM recipe_versions
                        ORDER BY dish_id, version_number DESC
                        """)
                .query(String.class)
                .list()
                .stream()
                .map(this::recipe)
                .flatMap(Optional::stream)
                .toList();
    }

    public Optional<RecipeVersion> recipe(String id) {
        return jdbc.sql("""
                        SELECT id,
                               dish_id,
                               version_number,
                               change_reason,
                               created_at,
                               active
                        FROM recipe_versions
                        WHERE id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) ->
                        new RecipeVersion(
                                rs.getString("id"),
                                rs.getString("dish_id"),
                                rs.getInt("version_number"),
                                recipeIngredients(rs.getString("id")),
                                recipeSteps(rs.getString("id")),
                                rs.getString("change_reason"),
                                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                                rs.getBoolean("active")))
                .optional();
    }

    private List<RecipeVersion.RecipeIngredient> recipeIngredients(String id) {
        return jdbc.sql("""
                        SELECT ingredient_id, quantity, unit
                        FROM recipe_ingredients
                        WHERE recipe_version_id = :id
                        ORDER BY ingredient_id
                        """)
                .param("id", id)
                .query((rs, rowNum) ->
                        new RecipeVersion.RecipeIngredient(
                                rs.getString("ingredient_id"),
                                rs.getDouble("quantity"),
                                rs.getString("unit")))
                .list();
    }

    private List<String> recipeSteps(String id) {
        return jdbc.sql("""
                        SELECT instruction
                        FROM recipe_steps
                        WHERE recipe_version_id = :id
                        ORDER BY step_number
                        """)
                .param("id", id)
                .query(String.class)
                .list();
    }

    public int nextRecipeVersion(String dishId) {

        Integer current = jdbc.sql("""
                        SELECT MAX(version_number)
                        FROM recipe_versions
                        WHERE dish_id = :dish
                        """)
                .param("dish", dishId)
                .query(Integer.class)
                .optional()
                .orElse(null);

        return current == null ? 1 : current + 1;
    }

    @Transactional
    public RecipeVersion saveRecipeProposal(RecipeVersion recipe) {

        jdbc.sql("""
                        INSERT INTO recipe_versions
                        (id, dish_id, version_number, change_reason, active, created_at)
                        VALUES
                        (:id, :dish, :version, :reason, FALSE, :created)
                        """)
                .param("id", recipe.id())
                .param("dish", recipe.dishId())
                .param("version", recipe.versionNumber())
                .param("reason", recipe.changeReason())
                .param("created", recipe.createdAt())
                .update();

        for (RecipeVersion.RecipeIngredient item : recipe.ingredients()) {
            jdbc.sql("""
                            INSERT INTO recipe_ingredients
                            (recipe_version_id, ingredient_id, quantity, unit)
                            VALUES
                            (:recipe, :ingredient, :quantity, :unit)
                            """)
                    .param("recipe", recipe.id())
                    .param("ingredient", item.ingredientId())
                    .param("quantity", item.quantity())
                    .param("unit", item.unit())
                    .update();
        }

        for (int index = 0; index < recipe.instructions().size(); index++) {
            jdbc.sql("""
                            INSERT INTO recipe_steps
                            (recipe_version_id, step_number, instruction)
                            VALUES
                            (:recipe, :step, :instruction)
                            """)
                    .param("recipe", recipe.id())
                    .param("step", index + 1)
                    .param("instruction", recipe.instructions().get(index))
                    .update();
        }

        addActivity(
                "Recipe proposal",
                "Recipe version " + recipe.versionNumber()
                        + " is waiting for owner approval");

        return recipe;
    }

    @Transactional
    public RecipeVersion createDishWithRecipe(
            Dish dish,
            int preparationMinutes,
            RecipeVersion recipe) {

        jdbc.sql("""
                        INSERT INTO dishes
                        (id, name, price, active_recipe_version_id,
                         active, kitchen_id, category_id, preparation_minutes)
                        VALUES
                        (:id, :name, :price, NULL,
                         TRUE, 'kitchen-default', :category, :minutes)
                        """)
                .param("id", dish.id())
                .param("name", dish.name())
                .param("price", dish.price())
                .param("category", dish.categoryId())
                .param("minutes", preparationMinutes)
                .update();

        jdbc.sql("""
                        INSERT INTO recipe_versions
                        (id, dish_id, version_number, change_reason, active, created_at)
                        VALUES
                        (:id, :dish, 1, :reason, TRUE, :created)
                        """)
                .param("id", recipe.id())
                .param("dish", dish.id())
                .param("reason", recipe.changeReason())
                .param("created", recipe.createdAt())
                .update();

        for (RecipeVersion.RecipeIngredient item : recipe.ingredients()) {
            jdbc.sql("""
                            INSERT INTO recipe_ingredients
                            (recipe_version_id, ingredient_id, quantity, unit)
                            VALUES
                            (:recipe, :ingredient, :quantity, :unit)
                            """)
                    .param("recipe", recipe.id())
                    .param("ingredient", item.ingredientId())
                    .param("quantity", item.quantity())
                    .param("unit", item.unit())
                    .update();
        }

        for (int index = 0; index < recipe.instructions().size(); index++) {
            jdbc.sql("""
                            INSERT INTO recipe_steps
                            (recipe_version_id, step_number, instruction)
                            VALUES
                            (:recipe, :step, :instruction)
                            """)
                    .param("recipe", recipe.id())
                    .param("step", index + 1)
                    .param("instruction", recipe.instructions().get(index))
                    .update();
        }

        jdbc.sql("""
                        UPDATE dishes
                        SET active_recipe_version_id = :recipe
                        WHERE id = :dish
                        """)
                .param("recipe", recipe.id())
                .param("dish", dish.id())
                .update();

        addActivity(
                "Menu",
                "Created " + dish.name() + " with recipe version 1");

        return recipe(recipe.id()).orElseThrow();
    }

    @Transactional
    public RecipeVersion activateRecipe(String recipeId) {

        RecipeVersion recipe = recipe(recipeId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Recipe version not found"));

        jdbc.sql("""
                        UPDATE recipe_versions
                        SET active = FALSE
                        WHERE dish_id = :dish
                        """)
                .param("dish", recipe.dishId())
                .update();

        jdbc.sql("""
                        UPDATE recipe_versions
                        SET active = TRUE
                        WHERE id = :id
                        """)
                .param("id", recipeId)
                .update();

        jdbc.sql("""
                        UPDATE dishes
                        SET active_recipe_version_id = :recipe
                        WHERE id = :dish
                        """)
                .param("recipe", recipeId)
                .param("dish", recipe.dishId())
                .update();

        addActivity(
                "Recipe approval",
                "Owner activated recipe version "
                        + recipe.versionNumber());

        return recipe(recipeId).orElseThrow();
    }

    // ============================================================
    // STOCK
    // ============================================================

    public List<StockLot> stockLots() {
        return jdbc.sql("""
                        SELECT id,
                               ingredient_id,
                               quantity_remaining,
                               unit,
                               purchased_at,
                               expires_at,
                               source
                        FROM stock_lots
                        WHERE quantity_remaining > 0
                        ORDER BY expires_at
                        """)
                .query(this::stockLot)
                .list();
    }

    public Optional<StockLot> firstAvailableLot(String ingredientId) {
        return jdbc.sql("""
                        SELECT id,
                               ingredient_id,
                               quantity_remaining,
                               unit,
                               purchased_at,
                               expires_at,
                               source
                        FROM stock_lots
                        WHERE ingredient_id = :ingredient
                          AND quantity_remaining > 0
                        ORDER BY expires_at
                        LIMIT 1
                        """)
                .param("ingredient", ingredientId)
                .query(this::stockLot)
                .optional();
    }

    @Transactional
    public StockLot addPurchase(StockLot lot) {

        jdbc.sql("""
                        INSERT INTO stock_lots
                        (id, ingredient_id, quantity_remaining,
                         unit, purchased_at, expires_at, source)
                        VALUES
                        (:id, :ingredient, :quantity,
                         :unit, :purchased, :expires, :source)
                        """)
                .param("id", lot.id())
                .param("ingredient", lot.ingredientId())
                .param("quantity", lot.quantityRemaining())
                .param("unit", lot.unit())
                .param("purchased", lot.purchasedAt())
                .param("expires", lot.expiresAt())
                .param("source", lot.source())
                .update();

        recordMovement(
                new StockMovement(
                        UUID.randomUUID().toString(),
                        lot.id(),
                        lot.ingredientId(),
                        StockMovement.MovementType.PURCHASE,
                        lot.quantityRemaining(),
                        lot.unit(),
                        "purchase",
                        lot.id(),
                        Instant.now()));

        return lot;
    }

    @Transactional
    public void consume(
            String ingredientId,
            double quantity,
            StockMovement.MovementType type,
            String referenceType,
            String referenceId) {

        double remaining = quantity;

        for (StockLot lot : stockLots().stream()
                .filter(item -> item.ingredientId().equals(ingredientId))
                .toList()) {

            if (remaining <= 0) {
                break;
            }

            double used = Math.min(
                    remaining,
                    lot.quantityRemaining());

            jdbc.sql("""
                            UPDATE stock_lots
                            SET quantity_remaining =
                                quantity_remaining - :used
                            WHERE id = :id
                            """)
                    .param("used", used)
                    .param("id", lot.id())
                    .update();

            recordMovement(
                    new StockMovement(
                            UUID.randomUUID().toString(),
                            lot.id(),
                            ingredientId,
                            type,
                            -used,
                            lot.unit(),
                            referenceType,
                            referenceId,
                            Instant.now()));

            remaining -= used;
        }

        if (remaining > 0.001) {
            throw new IllegalStateException(
                    "Not enough stock for " + ingredientId);
        }
    }

    public void recordMovement(StockMovement movement) {

        jdbc.sql("""
                        INSERT INTO stock_movements
                        (id, stock_lot_id, ingredient_id,
                         movement_type, quantity_change, unit,
                         reference_type, reference_id, occurred_at)
                        VALUES
                        (:id, :lot, :ingredient,
                         :type, :quantity, :unit,
                         :referenceType, :referenceId, :occurred)
                        """)
                .param("id", movement.id())
                .param("lot", movement.stockLotId())
                .param("ingredient", movement.ingredientId())
                .param("type", movement.type().name())
                .param("quantity", movement.quantityChange())
                .param("unit", movement.unit())
                .param("referenceType", movement.referenceType())
                .param("referenceId", movement.referenceId())
                .param("occurred", movement.occurredAt())
                .update();
    }

    public List<StockMovement> stockMovements() {

        return jdbc.sql("""
                        SELECT id,
                               stock_lot_id,
                               ingredient_id,
                               movement_type,
                               quantity_change,
                               unit,
                               reference_type,
                               reference_id,
                               occurred_at
                        FROM stock_movements
                        ORDER BY occurred_at DESC
                        """)
                .query((rs, rowNum) ->
                        new StockMovement(
                                rs.getString(1),
                                rs.getString(2),
                                rs.getString(3),
                                StockMovement.MovementType.valueOf(
                                        rs.getString(4)),
                                rs.getDouble(5),
                                rs.getString(6),
                                rs.getString(7),
                                rs.getString(8),
                                rs.getObject(9, OffsetDateTime.class)
                                        .toInstant()))
                .list();
    }

    // ============================================================
    // INVENTORY
    // ============================================================

    public List<InventoryLot> searchStockLots(
            String query,
            String status,
            int page,
            int size) {

        String normalizedQuery =
                query == null ? "" : query.trim().toLowerCase();

        String normalizedStatus =
                status == null ? "all" : status.trim().toLowerCase();

        LocalDate today = LocalDate.now();
        LocalDate soon = today.plusDays(7);

        return jdbc.sql("""
                        SELECT l.id,
                               l.ingredient_id,
                               i.name,
                               l.quantity_remaining,
                               l.unit,
                               l.purchased_at,
                               l.expires_at,
                               l.source,
                               CASE
                                   WHEN l.expires_at < :today
                                       THEN 'expired'
                                   WHEN l.expires_at <= :soon
                                       THEN 'expiring'
                                   ELSE 'available'
                               END AS lot_status
                        FROM stock_lots l
                        JOIN ingredients i
                            ON i.id = l.ingredient_id
                        WHERE l.quantity_remaining > 0
                          AND (
                              :query = ''
                              OR LOWER(i.name) LIKE :pattern
                              OR LOWER(l.id) LIKE :pattern
                          )
                          AND (
                              :status = 'all'
                              OR (
                                  :status = 'expired'
                                  AND l.expires_at < :today
                              )
                              OR (
                                  :status = 'expiring'
                                  AND l.expires_at >= :today
                                  AND l.expires_at <= :soon
                              )
                              OR (
                                  :status = 'available'
                                  AND l.expires_at > :soon
                              )
                          )
                        ORDER BY l.expires_at, i.name, l.id
                        LIMIT :size OFFSET :offset
                        """)
                .param("today", today)
                .param("soon", soon)
                .param("query", normalizedQuery)
                .param("pattern", "%" + normalizedQuery + "%")
                .param("status", normalizedStatus)
                .param("size", size)
                .param("offset", page * size)
                .query((rs, rowNum) ->
                        new InventoryLot(
                                rs.getString(1),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getDouble(4),
                                rs.getString(5),
                                rs.getObject(6, LocalDate.class),
                                rs.getObject(7, LocalDate.class),
                                rs.getString(8),
                                rs.getString(9)))
                .list();
    }

    public long countStockLots(String query, String status) {

        String normalizedQuery =
                query == null ? "" : query.trim().toLowerCase();

        String normalizedStatus =
                status == null ? "all" : status.trim().toLowerCase();

        LocalDate today = LocalDate.now();
        LocalDate soon = today.plusDays(7);

        return jdbc.sql("""
                        SELECT COUNT(*)
                        FROM stock_lots l
                        JOIN ingredients i
                            ON i.id = l.ingredient_id
                        WHERE l.quantity_remaining > 0
                          AND (
                              :query = ''
                              OR LOWER(i.name) LIKE :pattern
                              OR LOWER(l.id) LIKE :pattern
                          )
                          AND (
                              :status = 'all'
                              OR (
                                  :status = 'expired'
                                  AND l.expires_at < :today
                              )
                              OR (
                                  :status = 'expiring'
                                  AND l.expires_at >= :today
                                  AND l.expires_at <= :soon
                              )
                              OR (
                                  :status = 'available'
                                  AND l.expires_at > :soon
                              )
                          )
                        """)
                .param("today", today)
                .param("soon", soon)
                .param("query", normalizedQuery)
                .param("pattern", "%" + normalizedQuery + "%")
                .param("status", normalizedStatus)
                .query(Long.class)
                .single();
    }

    public InventorySummary inventorySummary() {

        LocalDate today = LocalDate.now();
        LocalDate soon = today.plusDays(7);

        return jdbc.sql("""
                        SELECT
                            COUNT(DISTINCT ingredient_id),
                            COUNT(*),
                            COALESCE(SUM(
                                CASE
                                    WHEN expires_at >= :today
                                     AND expires_at <= :soon
                                    THEN 1
                                    ELSE 0
                                END
                            ), 0),
                            COALESCE(SUM(
                                CASE
                                    WHEN expires_at < :today
                                    THEN 1
                                    ELSE 0
                                END
                            ), 0)
                        FROM stock_lots
                        WHERE quantity_remaining > 0
                        """)
                .param("today", today)
                .param("soon", soon)
                .query((rs, rowNum) ->
                        new InventorySummary(
                                rs.getLong(1),
                                rs.getLong(2),
                                rs.getLong(3),
                                rs.getLong(4)))
                .single();
    }

    // ============================================================
    // ORDERS
    // ============================================================

    public List<Order> orders() {
        return jdbc.sql("""
                        SELECT id, total, status, created_at
                        FROM customer_orders
                        ORDER BY created_at DESC
                        """)
                .query((rs, rowNum) ->
                        new Order(
                                rs.getString(1),
                                orderItems(rs.getString(1)),
                                rs.getBigDecimal(2),
                                Order.Status.valueOf(rs.getString(3)),
                                rs.getObject(4, OffsetDateTime.class)
                                        .toInstant()))
                .list();
    }

    public Optional<Order> order(String id) {
        return jdbc.sql("""
                        SELECT id, total, status, created_at
                        FROM customer_orders
                        WHERE id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) ->
                        new Order(
                                rs.getString(1),
                                orderItems(rs.getString(1)),
                                rs.getBigDecimal(2),
                                Order.Status.valueOf(rs.getString(3)),
                                rs.getObject(4, OffsetDateTime.class)
                                        .toInstant()))
                .optional();
    }

    public List<OrderListItem> searchOrders(
            String query,
            String status,
            int page,
            int size) {

        String normalizedQuery =
                query == null ? "" : query.trim().toLowerCase();

        String normalizedStatus =
                status == null ? "ALL" : status.trim().toUpperCase();

        return jdbc.sql("""
                        SELECT o.id,
                               o.total,
                               o.status,
                               o.created_at,
                               COALESCE(SUM(oi.quantity), 0) AS item_count
                        FROM customer_orders o
                        LEFT JOIN order_items oi
                            ON oi.order_id = o.id
                        WHERE (
                            :query = ''
                            OR LOWER(o.id) LIKE :pattern
                        )
                        AND (
                            :status = 'ALL'
                            OR o.status = :status
                        )
                        GROUP BY o.id, o.total, o.status, o.created_at
                        ORDER BY o.created_at DESC, o.id DESC
                        LIMIT :size OFFSET :offset
                        """)
                .param("query", normalizedQuery)
                .param("pattern", "%" + normalizedQuery + "%")
                .param("status", normalizedStatus)
                .param("size", size)
                .param("offset", page * size)
                .query((rs, rowNum) ->
                        new OrderListItem(
                                rs.getString(1),
                                rs.getBigDecimal(2),
                                Order.Status.valueOf(rs.getString(3)),
                                rs.getObject(4, OffsetDateTime.class)
                                        .toInstant(),
                                rs.getLong(5)))
                .list();
    }

    public long countOrders(String query, String status) {

        String normalizedQuery =
                query == null ? "" : query.trim().toLowerCase();

        String normalizedStatus =
                status == null ? "ALL" : status.trim().toUpperCase();

        return jdbc.sql("""
                        SELECT COUNT(*)
                        FROM customer_orders
                        WHERE (
                            :query = ''
                            OR LOWER(id) LIKE :pattern
                        )
                        AND (
                            :status = 'ALL'
                            OR status = :status
                        )
                        """)
                .param("query", normalizedQuery)
                .param("pattern", "%" + normalizedQuery + "%")
                .param("status", normalizedStatus)
                .query(Long.class)
                .single();
    }

    public OrderSummary orderSummary() {
        return jdbc.sql("""
                        SELECT
                            SUM(CASE
                                WHEN status IN ('QUEUED', 'PREPARING')
                                THEN 1 ELSE 0 END),

                            SUM(CASE
                                WHEN status = 'READY'
                                THEN 1 ELSE 0 END),

                            SUM(CASE
                                WHEN status = 'COMPLETED'
                                 AND CAST(created_at AS DATE) = CURRENT_DATE
                                THEN 1 ELSE 0 END),

                            COALESCE(SUM(CASE
                                WHEN status = 'CANCELLED'
                                 AND CAST(created_at AS DATE) = CURRENT_DATE
                                THEN total ELSE 0 END), 0)

                        FROM customer_orders
                        """)
                .query((rs, rowNum) ->
                        new OrderSummary(
                                rs.getLong(1),
                                rs.getLong(2),
                                rs.getLong(3),
                                rs.getBigDecimal(4)))
                .single();
    }

    @Transactional
    public Order updateOrderStatus(
            String id,
            Order.Status status) {

        int changed = jdbc.sql("""
                        UPDATE customer_orders
                        SET status = :status,
                            updated_at = :now
                        WHERE id = :id
                        """)
                .param("status", status.name())
                .param("now", Instant.now())
                .param("id", id)
                .update();

        if (changed == 0) {
            throw new IllegalArgumentException("Order not found");
        }

        jdbc.sql("""
                        INSERT INTO order_status_history
                        (id, order_id, status, changed_at)
                        VALUES
                        (:id, :order, :status, :changed)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("order", id)
                .param("status", status.name())
                .param("changed", Instant.now())
                .update();

        addActivity(
                "Order",
                id + " moved to "
                        + status.name().toLowerCase().replace('_', ' '));

        return order(id).orElseThrow();
    }

    private List<Order.OrderItem> orderItems(String id) {
        return jdbc.sql("""
                        SELECT dish_id, quantity, unit_price
                        FROM order_items
                        WHERE order_id = :id
                        ORDER BY line_number
                        """)
                .param("id", id)
                .query((rs, rowNum) ->
                        new Order.OrderItem(
                                rs.getString(1),
                                rs.getInt(2),
                                rs.getBigDecimal(3)))
                .list();
    }

    @Transactional
    public Order saveOrder(Order order) {

        jdbc.sql("""
                        INSERT INTO customer_orders
                        (id, total, status, created_at)
                        VALUES
                        (:id, :total, :status, :created)
                        """)
                .param("id", order.id())
                .param("total", order.total())
                .param("status", order.status().name())
                .param("created", order.createdAt())
                .update();

        for (int index = 0; index < order.items().size(); index++) {

            Order.OrderItem item = order.items().get(index);

            jdbc.sql("""
                            INSERT INTO order_items
                            (order_id, line_number, dish_id, quantity, unit_price)
                            VALUES
                            (:orderId, :line, :dish, :quantity, :price)
                            """)
                    .param("orderId", order.id())
                    .param("line", index + 1)
                    .param("dish", item.dishId())
                    .param("quantity", item.quantity())
                    .param("price", item.unitPrice())
                    .update();
        }

        int totalItems = order.items()
                .stream()
                .mapToInt(Order.OrderItem::quantity)
                .sum();

        addActivity(
                "Order",
                "Created " + order.id()
                        + " with " + totalItems + " items");

        return order;
    }

    // ============================================================
    // FEEDBACK
    // ============================================================

    public List<Feedback> feedback(String recipeId) {

        String sql;

        if (recipeId == null) {
            sql = """
                    SELECT id, recipe_id, feedback_text,
                           rating, occurred_at, source
                    FROM feedback
                    ORDER BY occurred_at DESC
                    """;
        } else {
            sql = """
                    SELECT id, recipe_id, feedback_text,
                           rating, occurred_at, source
                    FROM feedback
                    WHERE recipe_id = :recipe
                    ORDER BY occurred_at DESC
                    """;
        }

        var query = jdbc.sql(sql);

        if (recipeId != null) {
            query.param("recipe", recipeId);
        }

        return query.query((rs, rowNum) ->
                        new Feedback(
                                rs.getString(1),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getInt(4),
                                rs.getObject(5, LocalDate.class),
                                rs.getString(6)))
                .list();
    }

    public Feedback saveFeedback(Feedback value) {

        jdbc.sql("""
                        INSERT INTO feedback
                        (id, recipe_id, feedback_text,
                         rating, occurred_at, source)
                        VALUES
                        (:id, :recipe, :text,
                         :rating, :occurred, :source)
                        """)
                .param("id", value.id())
                .param("recipe", value.recipeId())
                .param("text", value.text())
                .param("rating", value.rating())
                .param("occurred", value.occurredAt())
                .param("source", value.source())
                .update();

        addActivity(
                "Feedback",
                "Captured feedback for " + value.recipeId());

        return value;
    }

    public void deleteFeedback(String id) {
        jdbc.sql("""
                        DELETE FROM feedback
                        WHERE id = :id
                        """)
                .param("id", id)
                .update();
    }

    // ============================================================
    // ACTIVITY
    // ============================================================

    public List<ActivityEvent> activities() {
        return jdbc.sql("""
                        SELECT id, event_type, description, occurred_at
                        FROM activity_events
                        ORDER BY occurred_at DESC
                        LIMIT 8
                        """)
                .query((rs, rowNum) ->
                        new ActivityEvent(
                                rs.getString(1),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getObject(4, OffsetDateTime.class)
                                        .toInstant()))
                .list();
    }

    public ActivityEvent addActivity(
            String type,
            String description) {

        ActivityEvent value = new ActivityEvent(
                UUID.randomUUID().toString(),
                type,
                description,
                Instant.now());

        jdbc.sql("""
                        INSERT INTO activity_events
                        (id, event_type, description, occurred_at)
                        VALUES
                        (:id, :type, :description, :occurred)
                        """)
                .param("id", value.id())
                .param("type", type)
                .param("description", description)
                .param("occurred", value.occurredAt())
                .update();

        analytics.publish(
                value.id(),
                type,
                Map.of("description", description),
                value.occurredAt());

        return value;
    }

    // ============================================================
    // AI AUDIT
    // ============================================================

    public void auditAiAction(
            String type,
            String input,
            String itemId,
            double confidence,
            String decision,
            String explanation) {

        jdbc.sql("""
                        INSERT INTO ai_actions
                        (id, action_type, original_input,
                         normalized_item_id, confidence,
                         decision, explanation, occurred_at)
                        VALUES
                        (:id, :type, :input,
                         :item, :confidence,
                         :decision, :explanation, :occurred)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("type", type)
                .param("input", input)
                .param("item", itemId)
                .param("confidence", confidence)
                .param("decision", decision)
                .param("explanation", explanation)
                .param("occurred", Instant.now())
                .update();
    }

    // ============================================================
    // INGREDIENT ALIASES
    // ============================================================

    public List<AliasMatch> aliases() {
        return jdbc.sql("""
                        SELECT a.alias_normalized,
                               a.ingredient_id,
                               i.name,
                               a.confidence,
                               a.source
                        FROM ingredient_aliases a
                        JOIN ingredients i
                            ON i.id = a.ingredient_id
                        """)
                .query((rs, rowNum) ->
                        new AliasMatch(
                                rs.getString(1),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getDouble(4),
                                rs.getString(5)))
                .list();
    }

    // ============================================================
    // RECEIPTS
    // ============================================================

    @Transactional
    public ReceiptImport saveReceipt(ReceiptImport receipt) {

        jdbc.sql("""
                        INSERT INTO receipt_imports
                        (id, original_filename, object_uri,
                         status, merchant, purchase_date,
                         total, created_at)
                        VALUES
                        (:id, :filename, :uri,
                         :status, :merchant, :date,
                         :total, :created)
                        """)
                .param("id", receipt.id())
                .param("filename", receipt.originalFilename())
                .param("uri", receipt.objectUri())
                .param("status", receipt.status().name())
                .param("merchant", receipt.merchant())
                .param("date", receipt.purchaseDate())
                .param("total", receipt.total())
                .param("created", receipt.createdAt())
                .update();

        for (ReceiptImport.ReceiptItem item : receipt.items()) {

            jdbc.sql("""
                            INSERT INTO receipt_items
                            (id, receipt_id, raw_name,
                             ingredient_id, canonical_name,
                             quantity, unit, unit_price,
                             confidence, selected)
                            VALUES
                            (:id, :receipt, :raw,
                             :ingredient, :canonical,
                             :quantity, :unit, :price,
                             :confidence, :selected)
                            """)
                    .param("id", item.id())
                    .param("receipt", receipt.id())
                    .param("raw", item.rawName())
                    .param("ingredient", item.ingredientId())
                    .param("canonical", item.canonicalName())
                    .param("quantity", item.quantity())
                    .param("unit", item.unit())
                    .param("price", item.unitPrice())
                    .param("confidence", item.confidence())
                    .param("selected", item.selected())
                    .update();
        }

        return receipt;
    }

    public List<ReceiptImport> receipts() {
        return jdbc.sql("""
                        SELECT id, original_filename, object_uri,
                               status, merchant, purchase_date,
                               total, created_at
                        FROM receipt_imports
                        ORDER BY created_at DESC
                        """)
                .query((rs, rowNum) -> receipt(rs))
                .list();
    }

    public Optional<ReceiptImport> receipt(String id) {
        return jdbc.sql("""
                        SELECT id, original_filename, object_uri,
                               status, merchant, purchase_date,
                               total, created_at
                        FROM receipt_imports
                        WHERE id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> receipt(rs))
                .optional();
    }

    @Transactional
    public void replaceReceiptItems(
            String receiptId,
            List<ReceiptImport.ReceiptItem> items) {

        jdbc.sql("""
                        DELETE FROM receipt_items
                        WHERE receipt_id = :id
                        """)
                .param("id", receiptId)
                .update();

        for (ReceiptImport.ReceiptItem item : items) {

            jdbc.sql("""
                            INSERT INTO receipt_items
                            (id, receipt_id, raw_name,
                             ingredient_id, canonical_name,
                             quantity, unit, unit_price,
                             confidence, selected)
                            VALUES
                            (:id, :receipt, :raw,
                             :ingredient, :canonical,
                             :quantity, :unit, :price,
                             :confidence, :selected)
                            """)
                    .param("id", item.id())
                    .param("receipt", receiptId)
                    .param("raw", item.rawName())
                    .param("ingredient", item.ingredientId())
                    .param("canonical", item.canonicalName())
                    .param("quantity", item.quantity())
                    .param("unit", item.unit())
                    .param("price", item.unitPrice())
                    .param("confidence", item.confidence())
                    .param("selected", item.selected())
                    .update();
        }
    }

    @Transactional
    public void confirmReceipt(String id) {

        jdbc.sql("""
                        UPDATE receipt_imports
                        SET status = 'CONFIRMED',
                            confirmed_at = :now
                        WHERE id = :id
                        """)
                .param("now", Instant.now())
                .param("id", id)
                .update();

        addActivity(
                "Receipt",
                "Confirmed receipt " + id);
    }

    // ============================================================
    // EXPERIMENTS
    // ============================================================

    public ExperimentResponse experiment(String reference) {

        String id = experimentId(reference);

        return jdbc.sql("""
                        SELECT d.name,
                               e.theme,
                               e.theme_count,
                               e.feedback_count,
                               e.current_value,
                               e.proposed_value,
                               e.test_duration_days,
                               e.status
                        FROM recipe_experiments e
                        JOIN dishes d
                            ON d.id = e.dish_id
                        WHERE e.id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) ->
                        new ExperimentResponse(
                                rs.getString(1),
                                rs.getString(2),
                                rs.getInt(3),
                                rs.getInt(4),
                                rs.getInt(5),
                                rs.getInt(6),
                                rs.getInt(7),
                                ExperimentStatus.valueOf(
                                        rs.getString(8))))
                .optional()
                .orElseThrow(() ->
                        new IllegalArgumentException("Experiment not found"));
    }

    @Transactional
    public void approveExperiment(String reference) {

        String id = experimentId(reference);

        jdbc.sql("""
                        UPDATE recipe_experiments
                        SET status = 'ACTIVE',
                            approved_at = :now
                        WHERE id = :id
                        """)
                .param("now", Instant.now())
                .param("id", id)
                .update();

        addActivity(
                "Recipe experiment",
                "Owner approved a controlled recipe experiment");
    }

    private String experimentId(String reference) {

        return jdbc.sql("""
                        SELECT id
                        FROM recipe_experiments
                        WHERE id = :reference
                           OR dish_id = :reference
                        ORDER BY
                            CASE
                                WHEN id = :reference THEN 0
                                ELSE 1
                            END,
                            id DESC
                        LIMIT 1
                        """)
                .param("reference", reference)
                .query(String.class)
                .optional()
                .orElseThrow(() ->
                        new IllegalArgumentException("Experiment not found"));
    }

    // ============================================================
    // ROW MAPPERS
    // ============================================================

    private ReceiptImport receipt(ResultSet rs)
            throws SQLException {

        String id = rs.getString(1);

        List<ReceiptImport.ReceiptItem> items =
                jdbc.sql("""
                                SELECT id,
                                       raw_name,
                                       ingredient_id,
                                       canonical_name,
                                       quantity,
                                       unit,
                                       unit_price,
                                       confidence,
                                       selected
                                FROM receipt_items
                                WHERE receipt_id = :id
                                ORDER BY id
                                """)
                        .param("id", id)
                        .query((line, rowNum) ->
                                new ReceiptImport.ReceiptItem(
                                        line.getString(1),
                                        line.getString(2),
                                        line.getString(3),
                                        line.getString(4),
                                        line.getDouble(5),
                                        line.getString(6),
                                        line.getBigDecimal(7),
                                        line.getDouble(8),
                                        line.getBoolean(9)))
                        .list();

        OffsetDateTime created =
                rs.getObject(8, OffsetDateTime.class);

        return new ReceiptImport(
                id,
                rs.getString(2),
                rs.getString(3),
                ReceiptImport.Status.valueOf(rs.getString(4)),
                rs.getString(5),
                rs.getObject(6, LocalDate.class),
                rs.getBigDecimal(7),
                created.toInstant(),
                items);
    }

    public record AliasMatch(
            String alias,
            String ingredientId,
            String canonicalName,
            double confidence,
            String source) {
    }

    private Ingredient ingredient(
            ResultSet rs,
            int rowNum) throws SQLException {

        return new Ingredient(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getBoolean(4));
    }

    private Dish dish(
            ResultSet rs,
            int rowNum) throws SQLException {

        return new Dish(
                rs.getString(1),
                rs.getString(2),
                rs.getBigDecimal(3),
                rs.getString(4),
                rs.getBoolean(5),
                rs.getString(6),
                rs.getString(7));
    }

    private StockLot stockLot(
            ResultSet rs,
            int rowNum) throws SQLException {

        return new StockLot(
                rs.getString(1),
                rs.getString(2),
                rs.getDouble(3),
                rs.getString(4),
                rs.getObject(5, LocalDate.class),
                rs.getObject(6, LocalDate.class),
                rs.getString(7));
    }
}