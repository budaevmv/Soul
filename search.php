<?php
require_once 'config.php';
$query = trim($_GET['q'] ?? '');
$products = [];

if (!empty($query)) {
    $stmt = $pdo->prepare("SELECT product_id, name, price, link, img FROM products WHERE (name LIKE ? OR category LIKE ?) AND in_stock = 1 LIMIT 50");
    $stmt->execute(["%$query%", "%$query%"]);
    $products = $stmt->fetchAll();
}
?>
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
    <title>Поиск | Soul Candles</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:opsz,wght@14..32,300..500&family=Playfair+Display:ital,wght@0,400..700;1,400..700&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0-beta3/css/all.min.css">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Inter', sans-serif; background: #FFFFFF; color: #1E1E1E; line-height: 1.5; font-weight: 300; }
        .container { max-width: 1200px; margin: 0 auto; padding: 0 40px; }
        
        .search-box { text-align: center; margin: 60px 0 40px; }
        .search-box h1 { font-family: 'Playfair Display', serif; font-size: 2.5rem; margin-bottom: 20px; }
        .search-form { display: flex; justify-content: center; gap: 10px; max-width: 500px; margin: 0 auto; }
        .search-form input { flex: 1; padding: 14px 20px; border: 1px solid #ddd; border-radius: 999px; font-size: 1rem; outline: none; transition: border-color 0.3s; }
        .search-form input:focus { border-color: #8C6E5A; }
        .search-form button { padding: 14px 30px; background: #8C6E5A; color: white; border: none; border-radius: 999px; cursor: pointer; font-size: 1rem; transition: background 0.3s; }
        .search-form button:hover { background: #1E3A2F; }
        
        .results-count { text-align: center; color: #5A5A5A; margin-bottom: 30px; }
        .product-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 30px; }
        .product-card { background: #FFFFFF; text-decoration: none; color: inherit; display: flex; flex-direction: column; transition: transform 0.3s ease; border-radius: 20px; overflow: hidden; cursor: pointer; border: 1px solid rgba(0,0,0,0.05); }
        .product-card:hover { transform: translateY(-5px); box-shadow: 0 10px 30px rgba(0,0,0,0.08); }
        .product-card .product-img { width: 100%; aspect-ratio: 1 / 1; object-fit: cover; display: block; background: #F5F5F5; }
        .card-info { padding: 16px; text-align: center; }
        .product-card h3 { font-size: 1rem; font-weight: 500; margin-bottom: 6px; font-family: 'Inter', sans-serif; }
        .price { color: #8C6E5A; font-weight: 600; font-size: 1rem; }
        .empty { text-align: center; padding: 60px 20px; color: #aaa; }
        .empty h2 { font-family: 'Playfair Display', serif; font-size: 1.8rem; margin-bottom: 10px; }
        
        .back-link { display: inline-block; margin-top: 30px; color: #8C6E5A; text-decoration: none; }
        .back-link:hover { text-decoration: underline; }
        
        header { background: #FFFFFF; padding: 20px 0; border-bottom: 1px solid rgba(0,0,0,0.05); }
        .header-inner { display: flex; align-items: center; justify-content: space-between; }
        .logo { font-size: 1.8rem; font-weight: 500; color: #8C6E5A; text-decoration: none; }
        .logo span { font-weight: 300; color: #1E1E1E; }
        .nav-desktop ul { display: flex; list-style: none; gap: 48px; align-items: center; }
        .nav-desktop a { text-decoration: none; color: #1E1E1E; font-weight: 300; font-size: 0.95rem; text-transform: uppercase; letter-spacing: 1px; transition: color 0.3s; }
        .nav-desktop a:hover { color: #8C6E5A; }
        .mobile-menu-toggle { display: none; background: none; border: none; font-size: 1.5rem; cursor: pointer; }
        
        @media (max-width: 900px) {
            .container { padding: 0 24px; }
            .nav-desktop { display: none; }
            .mobile-menu-toggle { display: block; }
            .search-box { margin: 40px 0 30px; }
            .search-box h1 { font-size: 2rem; }
        }
        
        @media (max-width: 600px) {
            .container { padding: 0 20px; }
            .search-form { flex-direction: column; }
            .search-box h1 { font-size: 1.8rem; }
            .product-grid { gap: 20px; grid-template-columns: 1fr 1fr; }
        }
    </style>
</head>
<body>

<header>
    <div class="container header-inner">
        <a href="index.html" class="logo">Soul <span>Candles</span></a>
        <nav class="nav-desktop">
            <ul>
                <li><a href="catalog.html">Каталог</a></li>
                <li><a href="Aromas.html">Ароматы</a></li>
                <li><a href="index.html#about">Про нас</a></li>
                <li><a href="index.html#contacts">Контакты</a></li>
            </ul>
        </nav>
        <button class="mobile-menu-toggle" onclick="window.location.href='index.html'"><i class="fas fa-arrow-left"></i></button>
    </div>
</header>

<div class="container">
    <div class="search-box">
        <h1>🔍 Поиск ароматов</h1>
        <form class="search-form" method="GET">
            <input type="text" name="q" placeholder="Что ищете? Свеча, диффузор, аромат..." value="<?= htmlspecialchars($query) ?>" autofocus>
            <button type="submit">Найти</button>
        </form>
    </div>

    <?php if (!empty($query)): ?>
        <div class="results-count">Найдено товаров: <?= count($products) ?></div>
        
        <?php if (count($products) > 0): ?>
            <div class="product-grid">
                <?php foreach ($products as $product): ?>
                    <a href="<?= htmlspecialchars($product['link'] ?? '#') ?>" class="product-card">
                        <img class="product-img" src="<?= htmlspecialchars($product['img'] ?? 'https://i.ibb.co/7tvN62BM/1.jpg') ?>" alt="<?= htmlspecialchars($product['name']) ?>">
                        <div class="card-info">
                            <h3><?= htmlspecialchars($product['name']) ?></h3>
                            <div class="price"><?= number_format($product['price'], 0, '', ' ') ?> ₽</div>
                        </div>
                    </a>
                <?php endforeach; ?>
            </div>
        <?php else: ?>
            <div class="empty">
                <h2>😕 Ничего не найдено</h2>
                <p>Попробуйте изменить запрос или посмотреть <a href="catalog.html" style="color:#8C6E5A;">весь каталог</a></p>
            </div>
        <?php endif; ?>
    <?php else: ?>
        <div class="empty">
            <p>Введите запрос в поле выше, чтобы найти нужный аромат</p>
        </div>
    <?php endif; ?>
    
    <div style="text-align: center; margin-bottom: 60px;">
        <a href="index.html" class="back-link">← Вернуться на главную</a>
    </div>
</div>

</body>
</html>