<?php
session_start();
require_once 'config.php';
header('Content-Type: application/json');

$method = $_SERVER['REQUEST_METHOD'];

// GET — получить товары (публично)
if ($method === 'GET') {
    try {
        $stmt = $pdo->query("SELECT product_id, name, price, old_price, category, in_stock, link, img FROM products WHERE in_stock = 1 ORDER BY category, name");
        echo json_encode(['success' => true, 'products' => $stmt->fetchAll()]);
    } catch (Exception $e) {
        echo json_encode(['success' => false, 'error' => $e->getMessage()]);
    }
    exit;
}

// POST/PUT — только для админов
if (!isAdmin()) {
    http_response_code(403);
    echo json_encode(['success' => false, 'error' => 'Доступ запрещён']);
    exit;
}

// POST — обновить цену
if ($method === 'POST') {
    $data = json_decode(file_get_contents('php://input'), true);
    $productId = (int)($data['product_id'] ?? 0);
    $price = (int)($data['price'] ?? 0);

    if (!$productId || $price <= 0) {
        echo json_encode(['success' => false, 'error' => 'Неверные данные']);
        exit;
    }

    try {
        $stmt = $pdo->prepare("UPDATE products SET price = ? WHERE product_id = ?");
        $stmt->execute([$price, $productId]);
        echo json_encode(['success' => true]);
    } catch (Exception $e) {
        echo json_encode(['success' => false, 'error' => $e->getMessage()]);
    }
    exit;
}

// PUT — массовые скидки
if ($method === 'PUT') {
    $data = json_decode(file_get_contents('php://input'), true);
    $action = $data['action'] ?? '';
    $category = $data['category'] ?? '';
    $discountPercent = (int)($data['discount_percent'] ?? 0);

    try {
        if ($action === 'apply_discount' && $category && $discountPercent > 0) {
            $stmt = $pdo->prepare("UPDATE products SET old_price = price, price = ROUND(price * (1 - ?/100)) WHERE category = ? AND in_stock = 1");
            $stmt->execute([$discountPercent, $category]);
            echo json_encode(['success' => true, 'message' => "Скидка {$discountPercent}% применена"]);
        } elseif ($action === 'remove_discount' && $category) {
            $stmt = $pdo->prepare("UPDATE products SET price = old_price, old_price = NULL WHERE category = ? AND old_price IS NOT NULL");
            $stmt->execute([$category]);
            echo json_encode(['success' => true, 'message' => "Скидка снята"]);
        } else {
            echo json_encode(['success' => false, 'error' => 'Неверное действие']);
        }
    } catch (Exception $e) {
        echo json_encode(['success' => false, 'error' => $e->getMessage()]);
    }
    exit;
}
?>