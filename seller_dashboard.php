<?php
session_start();
require_once 'config.php';

if (!isset($_SESSION['role']) || $_SESSION['role'] !== 'seller') {
    header('Location: seller_login.php');
    exit;
}

$seller_id = $_SESSION['user_id'];

// Получаем товары с остатками ИЗ СКЛАДА ПРОДАВЦА
$products = $pdo->query("
    SELECT 
        p.product_id,
        p.name,
        p.category,
        p.price,
        COALESCE(ss.stock, 0) as seller_stock,
        GROUP_CONCAT(v.id ORDER BY v.flavor_name SEPARATOR '||') as variant_ids,
        GROUP_CONCAT(v.flavor_name ORDER BY v.flavor_name SEPARATOR '||') as flavor_names,
        GROUP_CONCAT(COALESCE(ss2.stock, 0) ORDER BY v.flavor_name SEPARATOR '||') as flavor_stocks
    FROM products p
    LEFT JOIN seller_stock ss ON p.product_id = ss.product_id 
        AND ss.seller_id = $seller_id 
        AND ss.variant_id IS NULL
    LEFT JOIN product_variants v ON p.product_id = v.product_id
    LEFT JOIN seller_stock ss2 ON v.id = ss2.variant_id 
        AND ss2.seller_id = $seller_id
    WHERE p.in_stock = 1
    GROUP BY p.product_id
    ORDER BY p.category, p.name
")->fetchAll();

// Если у товара нет записей в seller_stock — создаём
foreach ($products as &$product) {
    if ($product['seller_stock'] == 0 && empty($product['flavor_stocks'])) {
        // Проверяем, есть ли вообще записи для этого продавца
        $check = $pdo->prepare("SELECT COUNT(*) FROM seller_stock WHERE seller_id = ? AND product_id = ?");
        $check->execute([$seller_id, $product['product_id']]);
        if ($check->fetchColumn() == 0) {
            // Создаём запись с остатком 0
            $stmt = $pdo->prepare("INSERT INTO seller_stock (seller_id, product_id, stock) VALUES (?, ?, 0)");
            $stmt->execute([$seller_id, $product['product_id']]);
            $product['seller_stock'] = 0;
        }
    }
}

$today = date('Y-m-d');
$stmt = $pdo->prepare("SELECT SUM(quantity) as total_items, SUM(total) as total_sum FROM sales WHERE DATE(sale_date) = ? AND seller_id = ?");
$stmt->execute([$today, $seller_id]);
$todaySales = $stmt->fetch();

$firstDay = date('Y-m-01');
$stmt = $pdo->prepare("SELECT SUM(total) as month_sum FROM sales WHERE DATE(sale_date) >= ? AND seller_id = ?");
$stmt->execute([$firstDay, $seller_id]);
$monthSales = $stmt->fetch();
?>
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
    <title>Кабинет продавца | Soul Candles</title>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0-beta3/css/all.min.css">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Inter', sans-serif;
            background: #F5F0EB;
            color: #1E1E1E;
            padding: 15px;
        }
        .container { max-width: 1400px; margin: 0 auto; }
        .header {
            background: white;
            border-radius: 16px;
            padding: 15px 20px;
            margin-bottom: 20px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.05);
        }
        .header h1 {
            font-family: 'Playfair Display', serif;
            font-size: 1.3rem;
            color: #8C6E5A;
        }
        .header-right {
            display: flex;
            align-items: center;
            gap: 12px;
            flex-wrap: wrap;
            font-size: 0.85rem;
        }
        .logout-btn {
            background: #1E1E1E;
            color: white;
            padding: 6px 16px;
            border-radius: 999px;
            text-decoration: none;
            font-size: 0.8rem;
            transition: background 0.3s;
            border: none;
            cursor: pointer;
        }
        .logout-btn:hover { background: #8C6E5A; }
        
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
            gap: 12px;
            margin-bottom: 20px;
        }
        .stat-card {
            background: white;
            border-radius: 12px;
            padding: 15px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.05);
        }
        .stat-card .label {
            font-size: 0.7rem;
            color: #8C6E5A;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        .stat-card .value {
            font-size: 1.5rem;
            font-weight: 600;
            margin-top: 3px;
        }
        
        .products-section {
            background: white;
            border-radius: 16px;
            padding: 15px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.05);
        }
        .products-section h2 {
            font-size: 1.1rem;
            margin-bottom: 15px;
        }
        .table-wrap {
            overflow-x: auto;
            -webkit-overflow-scrolling: touch;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            min-width: 600px;
        }
        th, td {
            padding: 10px 12px;
            text-align: left;
            border-bottom: 1px solid #eee;
            font-size: 0.85rem;
        }
        th {
            background: #F9F8F6;
            font-weight: 600;
            font-size: 0.7rem;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: #5A5A5A;
        }
        .product-name { font-weight: 500; }
        .stock-badge {
            display: inline-block;
            padding: 3px 10px;
            border-radius: 999px;
            font-size: 0.7rem;
            font-weight: 500;
        }
        .stock-high { background: #E8F5E9; color: #2E7D32; }
        .stock-medium { background: #FFF3E0; color: #E65100; }
        .stock-low { background: #FFEBEE; color: #C62828; }
        
        .flavor-select {
            padding: 5px 8px;
            border: 1px solid #ddd;
            border-radius: 6px;
            font-family: inherit;
            font-size: 0.8rem;
            background: white;
            min-width: 120px;
            max-width: 160px;
        }
        .flavor-select:focus { outline: none; border-color: #8C6E5A; }
        
        .quantity-input {
            width: 50px;
            padding: 6px 8px;
            border: 1px solid #ddd;
            border-radius: 6px;
            font-size: 0.85rem;
            text-align: center;
        }
        .sell-btn {
            background: #8C6E5A;
            color: white;
            border: none;
            padding: 6px 14px;
            border-radius: 6px;
            cursor: pointer;
            font-size: 0.8rem;
            transition: background 0.3s;
            white-space: nowrap;
        }
        .sell-btn:hover { background: #1E3A2F; }
        .sell-btn:disabled {
            background: #ccc;
            cursor: not-allowed;
        }
        
        .filter-bar {
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
            margin-bottom: 15px;
            align-items: center;
        }
        .filter-btn {
            padding: 5px 14px;
            border: 1px solid #ddd;
            border-radius: 999px;
            background: white;
            cursor: pointer;
            font-size: 0.75rem;
            transition: all 0.3s;
        }
        .filter-btn:hover, .filter-btn.active {
            background: #8C6E5A;
            color: white;
            border-color: #8C6E5A;
        }
        .search-input {
            padding: 6px 14px;
            border: 1px solid #ddd;
            border-radius: 999px;
            font-family: inherit;
            font-size: 0.8rem;
            width: 150px;
        }
        .search-input:focus { outline: none; border-color: #8C6E5A; }
        
        @media (max-width: 768px) {
            body { padding: 10px; }
            .header { flex-direction: column; text-align: center; padding: 12px 15px; }
            .header h1 { font-size: 1.1rem; }
            .stats-grid { grid-template-columns: 1fr 1fr; gap: 8px; }
            .stat-card { padding: 12px; }
            .stat-card .value { font-size: 1.2rem; }
            .filter-bar { justify-content: center; }
            .search-input { width: 100%; }
            .filter-btn { font-size: 0.7rem; padding: 4px 12px; }
            .products-section { padding: 10px; }
            .products-section h2 { font-size: 1rem; text-align: center; }
            table { min-width: 500px; font-size: 0.75rem; }
            th, td { padding: 8px 8px; }
            .flavor-select { min-width: 80px; max-width: 120px; font-size: 0.7rem; padding: 4px 6px; }
            .quantity-input { width: 40px; padding: 4px 6px; font-size: 0.75rem; }
            .sell-btn { font-size: 0.7rem; padding: 4px 10px; }
        }
        
        @media (max-width: 480px) {
            .stats-grid { grid-template-columns: 1fr 1fr; gap: 6px; }
            .stat-card .value { font-size: 1rem; }
            table { min-width: 400px; font-size: 0.7rem; }
            th, td { padding: 6px 5px; }
            .flavor-select { min-width: 60px; max-width: 90px; font-size: 0.65rem; }
            .quantity-input { width: 35px; padding: 3px 4px; }
            .sell-btn { font-size: 0.65rem; padding: 3px 8px; }
        }
        
        .toast {
            position: fixed;
            bottom: 20px;
            left: 50%;
            transform: translateX(-50%);
            background: #1E1E1E;
            color: white;
            padding: 10px 20px;
            border-radius: 40px;
            font-size: 0.85rem;
            z-index: 999;
            animation: fadeInOut 3s ease forwards;
            max-width: 90%;
            text-align: center;
        }
        @keyframes fadeInOut {
            0% { opacity: 0; transform: translateX(-50%) translateY(20px); }
            15% { opacity: 1; transform: translateX(-50%) translateY(0); }
            85% { opacity: 1; transform: translateX(-50%) translateY(0); }
            100% { opacity: 0; transform: translateX(-50%) translateY(-20px); }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>Кабинет продавца</h1>
            <div class="header-right">
                <span><i class="fas fa-user"></i> <?= htmlspecialchars($_SESSION['user_name']) ?></span>
                <a href="seller_logout.php" class="logout-btn">Выйти</a>
            </div>
        </div>
        
        <div class="stats-grid">
            <div class="stat-card">
                <div class="label">Продано сегодня</div>
                <div class="value" id="todayItems"><?= $todaySales['total_items'] ?? 0 ?> шт.</div>
            </div>
            <div class="stat-card">
                <div class="label">Выручка сегодня</div>
                <div class="value" id="todaySum"><?= number_format($todaySales['total_sum'] ?? 0, 0, '', ' ') ?> ₽</div>
            </div>
            <div class="stat-card">
                <div class="label">Выручка за месяц</div>
                <div class="value" id="monthSum"><?= number_format($monthSales['month_sum'] ?? 0, 0, '', ' ') ?> ₽</div>
            </div>
        </div>
        
        <div class="products-section">
            <h2>Продажа товаров</h2>
            
            <div class="filter-bar">
                <input type="text" class="search-input" id="searchInput" placeholder="Поиск...">
                <button class="filter-btn active" data-filter="all">Все</button>
                <button class="filter-btn" data-filter="candle">Свечи</button>
                <button class="filter-btn" data-filter="home">Дом</button>
                <button class="filter-btn" data-filter="solid">Духи</button>
                <button class="filter-btn" data-filter="auto">Авто</button>
                <button class="filter-btn" data-filter="set">Наборы</button>
            </div>
            
            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>Товар</th>
                            <th>Цена</th>
                            <th>Аромат</th>
                            <th>Остаток</th>
                            <th>Кол-во</th>
                            <th>Действие</th>
                        </tr>
                    </thead>
                    <tbody id="productsTable">
                        <?php foreach($products as $product): 
                            $flavorNames = explode('||', $product['flavor_names'] ?? '');
                            $variantIds = explode('||', $product['variant_ids'] ?? '');
                            $flavorStocks = explode('||', $product['flavor_stocks'] ?? '');
                            
                            // Если нет ароматов
                            if (empty($flavorNames[0]) || $flavorNames[0] == '') {
                                $flavorNames = ['Стандартный'];
                                $variantIds = ['0'];
                                $flavorStocks = [$product['seller_stock'] ?? 0];
                            }
                        ?>
                        <tr data-id="<?= $product['product_id'] ?>" data-category="<?= $product['category'] ?>" data-name="<?= strtolower($product['name']) ?>">
                            <td class="product-name"><?= htmlspecialchars($product['name']) ?></td>
                            <td><?= number_format($product['price'], 0, '', ' ') ?> ₽</td>
                            <td>
                                <select class="flavor-select" id="flavor-<?= $product['product_id'] ?>">
                                    <?php foreach($flavorNames as $index => $flavorName): 
                                        $variantId = $variantIds[$index] ?? 0;
                                        $stock = $flavorStocks[$index] ?? 0;
                                    ?>
                                        <option value="<?= $variantId ?>" data-stock="<?= $stock ?>">
                                            <?= htmlspecialchars($flavorName) ?> (<?= $stock ?> шт.)
                                        </option>
                                    <?php endforeach; ?>
                                </select>
                            </td>
                            <td>
                                <span class="stock-badge stock-display" id="stock-<?= $product['product_id'] ?>">
                                    <?php 
                                    $totalStock = array_sum($flavorStocks);
                                    $stockClass = $totalStock > 10 ? 'stock-high' : ($totalStock > 3 ? 'stock-medium' : 'stock-low');
                                    ?>
                                    <span class="stock-badge <?= $stockClass ?>"><?= $totalStock ?> шт.</span>
                                </span>
                            </td>
                            <td>
                                <input type="number" class="quantity-input" min="1" max="99" value="1" id="qty-<?= $product['product_id'] ?>">
                            </td>
                            <td>
                                <button class="sell-btn" onclick="sellProduct('<?= $product['product_id'] ?>')">
                                    <i class="fas fa-cart-plus"></i>
                                </button>
                            </td>
                        </tr>
                        <?php endforeach; ?>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
    
    <script>
        // ... (скрипты те же, что и в предыдущей версии)
    </script>
</body>
</html>