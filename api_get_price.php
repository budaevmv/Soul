<?php
require_once 'config.php';
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');

try {
    $stmt = $pdo->query("SELECT product_id, id, name, price, old_price, category, in_stock FROM products ORDER BY category, name");
    $products = $stmt->fetchAll();
    
    echo json_encode([
        'success' => true, 
        'products' => $products
    ]);
} catch(Exception $e) {
    echo json_encode([
        'success' => false, 
        'error' => $e->getMessage()
    ]);
}
?>
