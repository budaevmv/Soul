<?php
require_once 'config.php';
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');

$productId = isset($_GET['product_id']) ? $_GET['product_id'] : '';

if (empty($productId)) {
    echo json_encode(['success' => false, 'error' => 'Не указан product_id']);
    exit;
}

try {
    $stmt = $pdo->prepare("
        SELECT id, flavor_name, price, stock 
        FROM product_variants 
        WHERE product_id = ? 
        ORDER BY flavor_name
    ");
    $stmt->execute([$productId]);
    $variants = $stmt->fetchAll();
    
    echo json_encode([
        'success' => true,
        'variants' => $variants
    ]);
} catch (Exception $e) {
    echo json_encode([
        'success' => false,
        'error' => $e->getMessage()
    ]);
}
?>
