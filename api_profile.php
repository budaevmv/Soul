<?php
session_start();
require_once 'config.php';
header('Content-Type: application/json');

if (!isLoggedIn()) {
    echo json_encode(['success' => false, 'error' => 'Не авторизован']);
    exit;
}

$userId = $_SESSION['user_id'];

try {
    $stmt = $pdo->prepare("SELECT id, name, email, phone, returns FROM users WHERE id = ?");
    $stmt->execute([$userId]);
    $user = $stmt->fetch();

    if (!$user) {
        echo json_encode(['success' => false, 'error' => 'Пользователь не найден']);
        exit;
    }

    $stmt = $pdo->prepare("SELECT order_number, items, total, status, created_at FROM orders WHERE user_id = ? ORDER BY created_at DESC");
    $stmt->execute([$userId]);
    $orders = $stmt->fetchAll();

    $stmt = $pdo->prepare("SELECT return_date, status FROM returns WHERE user_id = ? ORDER BY return_date DESC");
    $stmt->execute([$userId]);
    $returns = $stmt->fetchAll();

    echo json_encode([
        'success' => true,
        'user' => $user,
        'orders' => $orders,
        'returns' => $returns
    ], JSON_UNESCAPED_UNICODE);
} catch (Exception $e) {
    echo json_encode(['success' => false, 'error' => $e->getMessage()]);
}
?>