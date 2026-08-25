<?php
require_once 'config.php';
header('Content-Type: application/json; charset=utf-8');

$stmt = $pdo->query("SELECT product_id, name, price, old_price, category, img, link FROM products WHERE in_stock = 1 ORDER BY RAND() LIMIT 4");

echo json_encode([
    'success'  => true,
    'products' => $stmt->fetchAll(PDO::FETCH_ASSOC)
]);