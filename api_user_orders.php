<?php
session_start();
require_once 'config.php';
header('Content-Type: application/json; charset=utf-8');

$uid = isset($_SESSION['user_id']) ? (int)$_SESSION['user_id'] : 0;

if (!$uid) {
    echo json_encode(['authenticated' => false]);
    exit;
}

$stmt = $pdo->prepare("SELECT id, order_number, items, total, status, created_at FROM orders WHERE user_id = ? ORDER BY created_at DESC");
$stmt->execute([$uid]);

echo json_encode([
    'authenticated' => true,
    'orders'        => $stmt->fetchAll(PDO::FETCH_ASSOC)
]);