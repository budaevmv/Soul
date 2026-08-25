<?php
session_start();
require_once 'config.php';

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');

if (!isset($_SESSION['role']) || $_SESSION['role'] !== 'seller') {
    echo json_encode(['success' => false, 'error' => 'Доступ запрещён']);
    exit;
}

$action = $_GET['action'] ?? $_POST['action'] ?? '';

// GET — статистика
if ($action === 'stats') {
    $today = date('Y-m-d');
    $firstDay = date('Y-m-01');
    $sellerId = $_SESSION['user_id'];
    
    $stmt = $pdo->prepare("SELECT SUM(quantity) as today_items, SUM(total) as today_sum FROM sales WHERE DATE(sale_date) = ? AND seller_id = ?");
    $stmt->execute([$today, $sellerId]);
    $today = $stmt->fetch();
    
    $stmt = $pdo->prepare("SELECT SUM(total) as month_sum FROM sales WHERE DATE(sale_date) >= ? AND seller_id = ?");
    $stmt->execute([$firstDay, $sellerId]);
    $month = $stmt->fetch();
    
    echo json_encode([
        'success' => true,
        'stats' => [
            'today_items' => (int)($today['today_items'] ?? 0),
            'today_sum' => (float)($today['today_sum'] ?? 0),
            'month_sum' => (float)($month['month_sum'] ?? 0)
        ]
    ]);
    exit;
}

// POST — продажа
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $data = json_decode(file_get_contents('php://input'), true);
    $productId = $data['product_id'] ?? '';
    $variantId = $data['variant_id'] ?? 0;
    $quantity = (int)($data['quantity'] ?? 0);
    $flavor = $data['flavor'] ?? 'Стандартный';
    
    if (!$productId || $quantity < 1) {
        echo json_encode(['success' => false, 'error' => 'Неверные данные']);
        exit;
    }
    
    try {
        $pdo->beginTransaction();
        
        // Получаем товар
        $stmt = $pdo->prepare("SELECT price, name FROM products WHERE product_id = ?");
        $stmt->execute([$productId]);
        $product = $stmt->fetch();
        
        if (!$product) {
            echo json_encode(['success' => false, 'error' => 'Товар не найден']);
            $pdo->rollBack();
            exit;
        }
        
        // Если есть variant_id — обновляем остаток в product_variants
        if ($variantId > 0) {
            $stmt = $pdo->prepare("SELECT stock FROM product_variants WHERE id = ? AND product_id = ?");
            $stmt->execute([$variantId, $productId]);
            $variant = $stmt->fetch();
            
            if (!$variant) {
                echo json_encode(['success' => false, 'error' => 'Аромат не найден']);
                $pdo->rollBack();
                exit;
            }
            
            if ($variant['stock'] < $quantity) {
                echo json_encode(['success' => false, 'error' => 'Недостаточно товара']);
                $pdo->rollBack();
                exit;
            }
            
            $newStock = $variant['stock'] - $quantity;
            $stmt = $pdo->prepare("UPDATE product_variants SET stock = ? WHERE id = ?");
            $stmt->execute([$newStock, $variantId]);
        } else {
            // Если нет вариаций — обновляем основной остаток
            $stmt = $pdo->prepare("SELECT stock FROM products WHERE product_id = ?");
            $stmt->execute([$productId]);
            $productStock = $stmt->fetch();
            
            if ($productStock['stock'] < $quantity) {
                echo json_encode(['success' => false, 'error' => 'Недостаточно товара']);
                $pdo->rollBack();
                exit;
            }
            
            $newStock = $productStock['stock'] - $quantity;
            $stmt = $pdo->prepare("UPDATE products SET stock = ? WHERE product_id = ?");
            $stmt->execute([$newStock, $productId]);
        }
        
        // Записываем продажу с ароматом
        $total = $product['price'] * $quantity;
        $productName = $product['name'] . ' (' . $flavor . ')';
        $stmt = $pdo->prepare("
            INSERT INTO sales (product_id, product_name, quantity, price, total, seller_id, sale_date) 
            VALUES (?, ?, ?, ?, ?, ?, NOW())
        ");
        $stmt->execute([
            $productId,
            $productName,
            $quantity,
            $product['price'],
            $total,
            $_SESSION['user_id']
        ]);
        
        $pdo->commit();
        
        echo json_encode([
            'success' => true,
            'new_stock' => $newStock ?? 0,
            'total' => $total
        ]);
        
    } catch(Exception $e) {
        $pdo->rollBack();
        echo json_encode(['success' => false, 'error' => $e->getMessage()]);
    }
    exit;
}

echo json_encode(['success' => false, 'error' => 'Неизвестное действие']);
?>