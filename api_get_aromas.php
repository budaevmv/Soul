<?php
require_once 'config.php';
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');

try {
    // Получаем все уникальные ароматы из товаров
    $stmt = $pdo->query("
        SELECT DISTINCT 
            p.product_id,
            p.name as product_name,
            p.category,
            p.price,
            p.img,
            GROUP_CONCAT(v.flavor_name SEPARATOR '||') as flavors,
            GROUP_CONCAT(v.price SEPARATOR '||') as flavor_prices,
            GROUP_CONCAT(v.id SEPARATOR '||') as flavor_ids,
            GROUP_CONCAT(v.stock SEPARATOR '||') as flavor_stocks
        FROM products p
        LEFT JOIN product_variants v ON p.product_id = v.product_id
        WHERE p.in_stock = 1
        GROUP BY p.product_id
        ORDER BY p.category, p.name
    ");
    
    $products = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
    // Преобразуем данные в удобный формат
    $result = [];
    foreach ($products as $product) {
        $flavors = [];
        if ($product['flavors']) {
            $names = explode('||', $product['flavors']);
            $prices = explode('||', $product['flavor_prices']);
            $ids = explode('||', $product['flavor_ids']);
            $stocks = explode('||', $product['flavor_stocks']);
            
            foreach ($names as $i => $name) {
                $flavors[] = [
                    'id' => (int)($ids[$i] ?? 0),
                    'name' => $name,
                    'price' => (float)($prices[$i] ?? $product['price']),
                    'stock' => (int)($stocks[$i] ?? 0)
                ];
            }
        }
        
        $result[] = [
            'product_id' => $product['product_id'],
            'name' => $product['product_name'],
            'category' => $product['category'],
            'price' => (float)$product['price'],
            'img' => $product['img'] ?? '',
            'flavors' => $flavors
        ];
    }
    
    echo json_encode([
        'success' => true,
        'products' => $result
    ]);
    
} catch (Exception $e) {
    echo json_encode([
        'success' => false,
        'error' => $e->getMessage()
    ]);
}
?>