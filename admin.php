<?php
session_start();
require_once 'config.php';
if (!function_exists('csrf_token')) { function csrf_token(){ if(empty($_SESSION['csrf_token'])) $_SESSION['csrf_token']=bin2hex(random_bytes(32)); return $_SESSION['csrf_token']; } }
if (!function_exists('verify_csrf')) { function verify_csrf(){ return isset($_POST['csrf_token']) && hash_equals($_SESSION['csrf_token']??'', $_POST['csrf_token']); } }

$action = $_GET['action'] ?? 'prices';
if ($action==='logout'){ session_destroy(); header('Location: admin_login.php'); exit; }
if (!isAdmin()){ header('Location: admin_login.php'); exit; }

// Авто-миграция: колонка stock в product_variants (если её ещё нет)
try { $pdo->query("SELECT stock FROM product_variants LIMIT 1"); }
catch (Exception $e) {
    try { $pdo->exec("ALTER TABLE product_variants ADD COLUMN stock INT NOT NULL DEFAULT 0"); } catch (Exception $e2) {}
}

// Ваши категории товаров (в нужном порядке)
$CATEGORIES_PREFERRED = [
    'Свечи и диффузоры',
    'Свечи 100 мл',
    'Свечи 50 мл',
    'Спрей для дома',
    'Авто парфюм',
    'Твердые духи 10 гр',
    'Твердые духи 15 гр',
    'Аромасаше',
];

if ($action==='export'){
    header('Content-Type: application/vnd.ms-excel');
    header('Content-Disposition: attachment; filename="orders_'.date('Y-m-d').'.xls"');
    $orders=$pdo->query("SELECT o.*, u.name AS user_name FROM orders o LEFT JOIN users u ON o.user_id=u.id ORDER BY o.created_at DESC")->fetchAll();
    echo "№ заказа\tКлиент\tТелефон\tТовары\tСумма\tСтатус\tДата\n";
    foreach ($orders as $o) echo $o['order_number']."\t".$o['customer_name']."\t".$o['customer_phone']."\t".str_replace(["\r\n","\r","\n"]," ",$o['items'])."\t".$o['total']."\t".$o['status']."\t".date('d.m.Y',strtotime($o['created_at']))."\n";
    exit;
}

$message=''; $error='';
function clean_price_int($raw){ $c=preg_replace('/[^0-9]/','',(string)$raw); return $c===''?0:(int)$c; }

if ($action==='add_return' && isset($_GET['user_id'])) {
    try {
        $pdo->beginTransaction();
        $pdo->prepare("INSERT INTO returns (user_id, status) VALUES (?, 'принят')")->execute([(int)$_GET['user_id']]);
        $pdo->prepare("UPDATE users SET returns = returns + 1 WHERE id = ?")->execute([(int)$_GET['user_id']]);
        $pdo->commit();
        header('Location: admin.php?action=users&msg=return_ok'); exit;
    } catch (Exception $e){ $pdo->rollBack(); header('Location: admin.php?action=users&err='.urlencode($e->getMessage())); exit; }
}
if ($action==='delete_user' && isset($_GET['user_id'])){ $pdo->prepare("DELETE FROM users WHERE id=? AND role!='admin'")->execute([(int)$_GET['user_id']]); header('Location: admin.php?action=users&msg=user_del'); exit; }
if ($action==='update_order_status' && isset($_GET['order_id']) && isset($_GET['status'])){ $pdo->prepare("UPDATE orders SET status=? WHERE id=?")->execute([$_GET['status'],(int)$_GET['order_id']]); header('Location: admin.php?action=orders&msg=order_ok'); exit; }
if ($action==='delete_seller' && isset($_GET['id'])){ $pdo->prepare("DELETE FROM users WHERE id=? AND role='seller'")->execute([(int)$_GET['id']]); header('Location: admin.php?action=sellers&msg=seller_del'); exit; }
if ($action==='delete_product' && isset($_GET['id'])){ $pdo->prepare("DELETE FROM products WHERE id=?")->execute([(int)$_GET['id']]); header('Location: admin.php?action=products&msg=prod_del'); exit; }

if ($_SERVER['REQUEST_METHOD']==='POST' && verify_csrf()) {
    if ($action==='products' && isset($_POST['add_product'])) {
        $pdo->prepare("INSERT INTO products (name, price, category, link, img, stock, in_stock) VALUES (?,?,?,?,?,?,1)")
            ->execute([trim($_POST['name']), clean_price_int($_POST['price']), $_POST['category'], trim($_POST['link']), trim($_POST['img']), (int)$_POST['stock']]);
        $message="✅ Товар добавлен";
    }
    if ($action==='prices' && isset($_POST['save_price'])) {
        $pid = isset($_POST['id']) ? (int)$_POST['id'] : 0;
        $price = clean_price_int($_POST['price']);
        if ($pid > 0) {
            $pdo->prepare("UPDATE products SET price=? WHERE id=?")->execute([$price, $pid]);
            $message='✅ Цена сохранена: '.$price.' ₽';
        } else { $error='❌ Ошибка: не найден ID товара'; }
    }
    // ---------- СКЛАД: обнуление всех остатков ----------
    if ($action==='inventory' && isset($_POST['reset_stocks'])) {
        $pdo->beginTransaction();
        $pdo->exec("UPDATE product_variants SET stock=0");
        $pdo->exec("UPDATE products SET stock=0");
        $pdo->commit();
        $message='✅ Все остатки обнулены';
    }
    // ---------- СКЛАД: массовое сохранение остатков ----------
    if ($action==='inventory' && isset($_POST['mass_update'])) {
        $pdo->beginTransaction();
        foreach (($_POST['variants'] ?? []) as $vid=>$stock) {
            $pdo->prepare("UPDATE product_variants SET stock=? WHERE id=?")->execute([(int)$stock,(int)$vid]);
        }
        foreach (($_POST['products'] ?? []) as $pid=>$stock) {
            $pdo->prepare("UPDATE products SET stock=? WHERE id=?")->execute([(int)$stock,(int)$pid]);
        }
        $pdo->exec("UPDATE products p SET stock=(SELECT COALESCE(SUM(v.stock),0) FROM product_variants v WHERE v.product_id=p.product_id) WHERE EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id=p.product_id)");
        $pdo->commit();
        $message='✅ Все остатки обновлены';
    }
    if ($action==='sellers' && isset($_POST['add_seller'])) {
        try {
            $pdo->prepare("INSERT INTO users (name, email, phone, password, role) VALUES (?,?,?,?, 'seller')")
                ->execute([trim($_POST['name']), trim($_POST['email']), trim($_POST['phone']), password_hash($_POST['password'], PASSWORD_DEFAULT)]);
            $message="✅ Продавец добавлен";
        } catch (PDOException $e){ $error=($e->errorInfo[1]==1062)?'Email занят':$e->getMessage(); }
    }
    if ($action==='seller_stock' && isset($_POST['mass_update'])) {
        $sid=(int)$_POST['seller_id']; $pdo->beginTransaction();
        foreach ($_POST['stocks'] as $k=>$s){
            list($pid,$vid)=explode('|',$k); $vid=$vid!==''?(int)$vid:null;
            $pdo->prepare("INSERT INTO seller_stock (seller_id, product_id, variant_id, stock) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE stock=?")->execute([$sid,(int)$pid,$vid,(int)$s,(int)$s]);
        }
        $pdo->commit(); $message='✅ Склад обновлён';
    }
    if ($action==='password' && isset($_POST['change_password'])) {
        if ($_POST['new_password']!==$_POST['confirm_password']) $error='Пароли не совпадают';
        elseif (strlen($_POST['new_password'])<8) $error='Минимум 8 символов';
        else {
            $u=$pdo->prepare("SELECT password FROM users WHERE id=?"); $u->execute([$_SESSION['user_id']]); $u=$u->fetch();
            if (password_verify($_POST['old_password'],$u['password'])){
                $pdo->prepare("UPDATE users SET password=? WHERE id=?")->execute([password_hash($_POST['new_password'],PASSWORD_DEFAULT),$_SESSION['user_id']]);
                $message='✅ Пароль изменён';
            } else $error='Неверный текущий пароль';
        }
    }
}

$messages=['return_ok'=>'✅ Возврат добавлен','user_del'=>'✅ Удалено','order_ok'=>'✅ Статус обновлён','seller_del'=>'✅ Удалено','prod_del'=>'✅ Удалено','stocks_reset'=>'✅ Все остатки обнулены'];
if (isset($_GET['msg']) && isset($messages[$_GET['msg']])) $message=$messages[$_GET['msg']];
if (isset($_GET['err'])) $error='❌ '.$_GET['err'];

if ($action==='seller_stock') {
    $sellers=$pdo->query("SELECT id,name FROM users WHERE role='seller' ORDER BY name")->fetchAll();
    $selected_seller=isset($_GET['seller_id'])?(int)$_GET['seller_id']:($sellers[0]['id']??0);
    if ($selected_seller){
        $products_ss=$pdo->query("SELECT id, product_id, name, price FROM products WHERE in_stock=1 ORDER BY name")->fetchAll();
        foreach ($products_ss as &$p){
            $st=$pdo->prepare("SELECT v.id, v.flavor_name, v.price AS variant_price, COALESCE(ss.stock,0) AS seller_stock, p.price AS product_price FROM product_variants v LEFT JOIN seller_stock ss ON v.id=ss.variant_id AND ss.seller_id=? AND ss.product_id=v.product_id JOIN products p ON v.product_id=p.product_id WHERE v.product_id=? ORDER BY v.flavor_name");
            $st->execute([$selected_seller,$p['product_id']]);
            $p['variants']=$st->fetchAll(); $p['default_variant']=$p['variants'][0]??null;
        } unset($p);
    }
}

// ---------- СКЛАД: категории из базы в вашем порядке ----------
if ($action==='inventory') {
    $existing=$pdo->query("SELECT DISTINCT category FROM products WHERE category IS NOT NULL AND category<>'' ORDER BY category")->fetchAll(PDO::FETCH_COLUMN);
    $cats=array_merge(
        array_values(array_intersect($CATEGORIES_PREFERRED, $existing)),
        array_values(array_diff($existing, $CATEGORIES_PREFERRED))
    );
    $prods_inv=$pdo->query("SELECT id, product_id, name, category, stock, link FROM products ORDER BY name")->fetchAll();
    foreach ($prods_inv as &$p){
        $st=$pdo->prepare("SELECT id,flavor_name,stock FROM product_variants WHERE product_id=? ORDER BY flavor_name");
        $st->execute([$p['product_id']]);
        $p['variants']=$st->fetchAll();
    } unset($p);
    $noCat=array_values(array_filter($prods_inv, function($p) use ($cats){ return !in_array($p['category'], $cats); }));
}

if ($action==='stats') {
    $todayStats=$pdo->prepare("SELECT SUM(quantity) i, SUM(total) t FROM sales WHERE DATE(sale_date)=?"); $todayStats->execute([date('Y-m-d')]); $todayStats=$todayStats->fetch();
    $weekStats=$pdo->prepare("SELECT SUM(total) t FROM sales WHERE DATE(sale_date)>=?"); $weekStats->execute([date('Y-m-d',strtotime('-7 days'))]); $weekStats=$weekStats->fetch();
    $monthStats=$pdo->prepare("SELECT SUM(total) t FROM sales WHERE DATE(sale_date)>=?"); $monthStats->execute([date('Y-m-01')]); $monthStats=$monthStats->fetch();
    $dailySales=$pdo->query("SELECT DATE(sale_date) d, SUM(total) t FROM sales WHERE DATE(sale_date)>=DATE_SUB(CURDATE(),INTERVAL 7 DAY) GROUP BY d ORDER BY d")->fetchAll();
}

$userName = $_SESSION['user_name'] ?? 'Римма';
$userInitial = mb_strtoupper(mb_substr($userName, 0, 1));
?>
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>Soul Candles — Admin</title>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=Playfair+Display:wght@400;500;600;700&display=swap" rel="stylesheet">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
<style>
:root {
    --bg: #F9F7F4;
    --surface: #FFFFFF;
    --border: #EAE5DF;
    --text: #1E1E1E;
    --muted: #888888;
    --primary: #1E3A2F;
    --accent: #8C6E5A;
    --accent-hover: #755B4A;
    --danger: #C62828;
    --success: #2E7D32;
}

* { margin: 0; padding: 0; box-sizing: border-box; }
html, body { overflow-x: hidden; }
body { font-family: 'Inter', sans-serif; background: var(--bg); color: var(--text); min-height: 100vh; }

.app { display: flex; min-height: 100vh; }
.main { min-width: 0; }

/* ---------- САЙДБАР ---------- */
.sidebar {
    width: 280px; background: var(--surface); border-right: 1px solid var(--border);
    padding: 32px 20px; position: fixed; height: 100vh; overflow-y: auto;
    transition: transform .3s ease; z-index: 100;
}
.logo {
    font-family: 'Playfair Display', serif; font-size: 1.6rem; text-align: center;
    color: var(--accent); margin-bottom: 40px; font-weight: 600;
}
.logo span { color: var(--primary); font-weight: 400; }

.nav-section { margin-bottom: 24px; }
.nav-label {
    font-size: .7rem; text-transform: uppercase; letter-spacing: 1.5px;
    color: var(--muted); margin-bottom: 12px; padding-left: 12px; font-weight: 600;
}
.nav-item {
    display: flex; align-items: center; gap: 14px; padding: 12px 16px; margin-bottom: 6px;
    border-radius: 12px; color: #555; text-decoration: none; font-size: .95rem; font-weight: 500; transition: all .2s;
}
.nav-item:hover { background: #F5F0EB; color: var(--primary); }
.nav-item.active { background: var(--primary); color: #fff; box-shadow: 0 4px 12px rgba(30, 58, 47, 0.15); }
.nav-item i { width: 20px; text-align: center; opacity: .8; }
.nav-item.active i { opacity: 1; }

/* ---------- ОСНОВНАЯ ЧАСТЬ ---------- */
.main { flex: 1; margin-left: 280px; padding: 32px 40px; }

/* ---------- ИСПРАВЛЕННЫЙ ХЕДЕР (flex вместо grid) ---------- */
.header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 32px;
    padding-bottom: 20px;
    border-bottom: 1px solid var(--border);
    flex-wrap: wrap;
}
.burger {
    display: none;
    width: 44px;
    height: 44px;
    border: 1px solid var(--border);
    background: var(--surface);
    border-radius: 12px;
    color: var(--primary);
    font-size: 1.2rem;
    cursor: pointer;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
}
.page-title {
    font-family: 'Playfair Display', serif;
    font-size: 1.8rem;
    font-weight: 600;
    text-align: center;
    color: var(--primary);
    flex: 1;
    margin: 0 10px;
}
/* ---------- ПЛАШКА ПОЛЬЗОВАТЕЛЯ (компактная) ---------- */
.user-badge {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 16px 8px 8px;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 100px;
    flex-shrink: 0;
    white-space: nowrap;
}
.avatar {
    width: 36px;
    height: 36px;
    background: var(--accent);
    color: #fff;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 600;
    font-size: .9rem;
    flex-shrink: 0;
}
.user-info {
    display: flex;
    flex-direction: column;
    line-height: 1.2;
}
.user-name {
    font-size: .9rem;
    font-weight: 600;
    color: var(--text);
}
.user-role {
    font-size: .75rem;
    color: var(--muted);
}

/* ---------- АЛЕРТЫ ---------- */
.alert { padding: 16px 20px; border-radius: 12px; margin-bottom: 24px; font-size: .9rem; font-weight: 500; display: flex; align-items: center; gap: 12px; }
.alert-success { background: #E8F5E9; border: 1px solid #C8E6C9; color: var(--success); }
.alert-error { background: #FFEBEE; border: 1px solid #FFCDD2; color: var(--danger); }

/* ---------- КАРТОЧКИ И ТАБЛИЦЫ ---------- */
.glass-card { background: var(--surface); border: 1px solid var(--border); border-radius: 20px; overflow: hidden; margin-bottom: 28px; box-shadow: 0 4px 20px rgba(0,0,0,0.03); }
.card-header { padding: 20px 24px; border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.card-title { font-family: 'Playfair Display', serif; font-size: 1.2rem; font-weight: 600; color: var(--primary); }

.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; }
th { padding: 14px 24px; text-align: left; font-size: .75rem; text-transform: uppercase; letter-spacing: 1px; color: var(--muted); background: #FAFAFA; border-bottom: 1px solid var(--border); }
td { padding: 16px 24px; border-bottom: 1px solid var(--border); font-size: .95rem; }
tr:last-child td { border-bottom: none; }
tr:hover td { background: #FCFAF8; }

.badge { display: inline-flex; padding: 6px 12px; border-radius: 100px; font-size: .75rem; font-weight: 600; white-space: nowrap; }
.badge-new { background: #FFF3E0; color: #E65100; }
.badge-proc { background: #E3F2FD; color: #1565C0; }
.badge-done { background: #E8F5E9; color: #2E7D32; }
.badge-cancel { background: #FFEBEE; color: #C62828; }

/* ---------- ФОРМЫ ---------- */
.form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 20px; padding: 24px; }
.input-group { display: flex; flex-direction: column; gap: 8px; }
.input-group label { font-size: .8rem; color: var(--muted); font-weight: 500; }
.input-modern {
    padding: 12px 16px; background: #FAFAFA; border: 1px solid var(--border); border-radius: 12px;
    color: var(--text); font-family: inherit; font-size: .95rem; transition: border-color .2s;
}
.input-modern:focus { outline: none; border-color: var(--accent); background: #fff; }

.btn { padding: 12px 24px; border-radius: 12px; font-weight: 600; font-size: .9rem; cursor: pointer; border: none; display: inline-flex; align-items: center; justify-content: center; gap: 8px; transition: all .2s; }
.btn-primary { background: var(--accent); color: #fff; }
.btn-primary:hover { background: var(--accent-hover); }
.btn-danger { background: #FFEBEE; color: var(--danger); border: 1px solid #FFCDD2; }
.btn-danger:hover { background: #FFCDD2; }
.btn-sm { padding: 8px 16px; font-size: .85rem; border-radius: 10px; }

/* ---------- ДАШБОРД ---------- */
.stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 20px; margin-bottom: 28px; }
.stat-card { background: var(--surface); border: 1px solid var(--border); border-radius: 20px; padding: 24px; box-shadow: 0 4px 20px rgba(0,0,0,0.03); }
.stat-icon { width: 48px; height: 48px; border-radius: 14px; display: flex; align-items: center; justify-content: center; font-size: 1.2rem; margin-bottom: 16px; }
.stat-icon.purple { background: #F3E5F5; color: #7B1FA2; }
.stat-icon.green { background: #E8F5E9; color: #2E7D32; }
.stat-icon.orange { background: #FFF3E0; color: #E65100; }
.stat-icon.pink { background: #FCE4EC; color: #C2185B; }
.stat-label { font-size: .85rem; color: var(--muted); margin-bottom: 8px; }
.stat-value { font-family: 'Playfair Display', serif; font-size: 1.8rem; font-weight: 600; color: var(--primary); }

/* ---------- АНАЛИТИКА ---------- */
.chart-row { display: flex; align-items: center; gap: 20px; padding: 12px 0; }
.chart-label { width: 60px; font-size: .85rem; color: var(--muted); }
.chart-track { flex: 1; height: 10px; background: #F5F0EB; border-radius: 100px; overflow: hidden; }
.chart-fill { height: 100%; background: linear-gradient(90deg, var(--accent), var(--primary)); border-radius: 100px; }
.chart-val { width: 90px; text-align: right; font-weight: 600; color: var(--primary); }

/* ---------- СКЛАД: сворачиваемые категории + списания ---------- */
.inv-cat {
    font-family: 'Playfair Display', serif; font-size: 1.15rem; color: var(--primary);
    margin: 18px 0 10px; font-weight: 600;
    display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap;
    cursor: pointer; user-select: none; padding: 10px 14px;
    background: var(--surface); border: 1px solid var(--border); border-radius: 14px;
}
.inv-cat:first-child { margin-top: 0; }
.inv-cat-name { display: flex; align-items: center; gap: 10px; }
.inv-chev { transition: transform .2s; font-size: .75rem; color: var(--accent); }
.inv-cat-block.open .inv-chev { transform: rotate(180deg); }
.inv-cat-total { font-family: 'Inter', sans-serif; font-size: .78rem; font-weight: 600; color: var(--accent); background: #F5F0EB; padding: 5px 12px; border-radius: 100px; }
.inv-cat-body { display: none; padding: 4px 2px 2px; }
.inv-cat-block.open .inv-cat-body { display: block; }

.inv-product { border: 1px solid var(--border); border-radius: 16px; padding: 14px 16px; margin-bottom: 12px; background: #FDFCFA; }
.inv-product-name { font-weight: 600; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
.inv-total { color: var(--muted); font-weight: 600; font-size: .8rem; background: #F5F0EB; padding: 4px 10px; border-radius: 100px; }
.inv-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 6px 0; }
.inv-flavor { color: #555; font-size: .9rem; }
.inv-flavor b { color: var(--primary); font-weight: 600; }

.inv-controls { display: flex; align-items: center; gap: 8px; }
.inv-step {
    width: 38px; height: 38px; border-radius: 12px; border: 1px solid var(--border);
    background: #fff; color: var(--primary); font-size: 1.05rem; font-weight: 700;
    cursor: pointer; transition: .15s; flex-shrink: 0;
}
.inv-step:active { transform: scale(.92); }
.inv-step.minus { color: var(--danger); }
.inv-step.plus { color: var(--success); }
.inv-input { width: 74px; text-align: center; padding: 9px 6px; }

/* ---------- МОБИЛЬНАЯ ВЕРСИЯ (до 1024px) ---------- */
.sidebar-overlay { display: none; position: fixed; inset: 0; background: rgba(0,0,0,.4); z-index: 99; opacity: 0; transition: opacity .3s; }
.sidebar-overlay.active { display: block; opacity: 1; }

@media (max-width: 1024px) {
    .sidebar { transform: translateX(-100%); box-shadow: 4px 0 20px rgba(0,0,0,0.1); }
    .sidebar.open { transform: translateX(0); }
    .main { margin-left: 0; padding: 16px; }

    .burger { display: flex; }
    .header { margin-bottom: 20px; padding-bottom: 16px; gap: 10px; }
    .page-title { font-size: 1.4rem; }
    .user-badge { padding: 6px 12px 6px 6px; gap: 8px; }
    .avatar { width: 32px; height: 32px; font-size: .8rem; }
    .user-name { font-size: .8rem; }
    .user-role { font-size: .65rem; }

    .stats-grid { grid-template-columns: 1fr 1fr; gap: 12px; }
    .stat-card { padding: 16px; }
    .stat-value { font-size: 1.4rem; }
    .form-grid { padding: 16px; grid-template-columns: 1fr; }
    .card-header { flex-direction: column; align-items: stretch; }
    .card-header .input-modern { width: 100% !important; }

    .table-wrap { position: relative; -webkit-overflow-scrolling: touch; }
    th, td { padding: 12px 10px; font-size: .82rem; }

    #tblP td { padding: 12px 8px; }
    #tblP input[name="price"] { width: 76px; padding: 10px 8px; text-align: right; }
    #tblP .btn-sm { padding: 10px 14px; }
}

/* ---------- УЗКИЕ ЭКРАНЫ (до 640px): таблицы → карточки ---------- */
@media (max-width: 640px) {
    table.m-cards { display: block; }
    table.m-cards thead { display: none; }
    table.m-cards tbody { display: block; padding: 12px; }
    table.m-cards tr {
        display: block; background: #FDFCFA; border: 1px solid var(--border);
        border-radius: 16px; padding: 14px; margin-bottom: 12px;
    }
    table.m-cards tr:last-child { margin-bottom: 0; }
    table.m-cards tr:hover td { background: transparent; }
    table.m-cards td {
        display: flex; justify-content: space-between; align-items: center; gap: 12px;
        border: none; padding: 5px 0; font-size: .85rem; min-width: 0;
    }
    table.m-cards td::before {
        content: attr(data-label);
        flex: 0 0 auto; font-size: .68rem; text-transform: uppercase;
        letter-spacing: .6px; color: var(--muted); font-weight: 600;
    }
    table.m-cards td.nowrap { white-space: nowrap; }
    table.m-cards td.break { word-break: break-all; text-align: right; }
    table.m-cards td.td-block { display: block; font-weight: 600; color: var(--primary); padding-bottom: 8px; }
    table.m-cards td.td-block::before { content: none; }
    table.m-cards td.td-actions { justify-content: flex-end; }
    table.m-cards td.td-actions::before { content: none; }
    table.m-cards td select.input-modern { flex: 1; min-width: 0; padding: 8px 10px; font-size: .82rem; }
    table.m-cards td input[type="number"].input-modern { width: 80px; }
}
</style>
</head>
<body>

<div class="sidebar-overlay" id="sidebarOverlay"></div>

<div class="app">
    <!-- ---------- САЙДБАР ---------- -->
    <aside class="sidebar" id="sidebar">
        <div class="logo">Soul <span>Admin</span></div>

        <div class="nav-section">
            <div class="nav-label">Основное</div>
            <a href="?action=prices" class="nav-item <?= $action==='prices'?'active':'' ?>"><i class="fas fa-tag"></i> Цены</a>
            <a href="?action=dashboard" class="nav-item <?= $action==='dashboard'?'active':'' ?>"><i class="fas fa-th-large"></i> Дашборд</a>
            <a href="?action=stats" class="nav-item <?= $action==='stats'?'active':'' ?>"><i class="fas fa-chart-line"></i> Аналитика</a>
        </div>

        <div class="nav-section">
            <div class="nav-label">Каталог</div>
            <a href="?action=products" class="nav-item <?= $action==='products'?'active':'' ?>"><i class="fas fa-box-open"></i> Товары</a>
            <a href="?action=inventory" class="nav-item <?= $action==='inventory'?'active':'' ?>"><i class="fas fa-cubes"></i> Склад</a>
            <a href="?action=seller_stock" class="nav-item <?= $action==='seller_stock'?'active':'' ?>"><i class="fas fa-store"></i> Склады продавцов</a>
        </div>

        <div class="nav-section">
            <div class="nav-label">Продажи</div>
            <a href="?action=orders" class="nav-item <?= $action==='orders'?'active':'' ?>"><i class="fas fa-shopping-cart"></i> Заказы</a>
            <a href="?action=users" class="nav-item <?= $action==='users'?'active':'' ?>"><i class="fas fa-users"></i> Клиенты</a>
            <a href="?action=sellers" class="nav-item <?= $action==='sellers'?'active':'' ?>"><i class="fas fa-user-tie"></i> Продавцы</a>
            <a href="?action=returns" class="nav-item <?= $action==='returns'?'active':'' ?>"><i class="fas fa-undo"></i> Возвраты</a>
        </div>

        <div class="nav-section">
            <div class="nav-label">Система</div>
            <a href="?action=export" class="nav-item"><i class="fas fa-file-export"></i> Экспорт</a>
            <a href="?action=password" class="nav-item <?= $action==='password'?'active':'' ?>"><i class="fas fa-shield-alt"></i> Безопасность</a>
            <a href="?action=logout" class="nav-item" style="color: var(--danger);"><i class="fas fa-sign-out-alt"></i> Выйти</a>
        </div>
    </aside>

    <!-- ---------- ОСНОВНОЙ КОНТЕНТ ---------- -->
    <main class="main">
        <!-- ИСПРАВЛЕННЫЙ ХЕДЕР -->
        <div class="header">
            <button class="burger" id="mobileToggle" aria-label="Меню"><i class="fas fa-bars"></i></button>
            <h1 class="page-title"><?php
                $titles=['dashboard'=>'Дашборд','stats'=>'Аналитика продаж','products'=>'Управление товарами','prices'=>'Цены','inventory'=>'Складской учёт','seller_stock'=>'Склады продавцов','sellers'=>'Продавцы','orders'=>'Заказы','users'=>'Клиенты','returns'=>'Возвраты','password'=>'Безопасность'];
                echo $titles[$action] ?? 'Панель управления';
            ?></h1>
            <div class="user-badge">
                <div class="avatar"><?= htmlspecialchars($userInitial) ?></div>
                <div class="user-info">
                    <div class="user-name"><?= htmlspecialchars($userName) ?></div>
                    <div class="user-role">Администратор</div>
                </div>
            </div>
        </div>

        <?php if($message): ?><div class="alert alert-success" id="alertSuccess"><i class="fas fa-check-circle"></i> <?= htmlspecialchars($message) ?></div><?php endif; ?>
        <?php if($error): ?><div class="alert alert-error"><i class="fas fa-exclamation-circle"></i> <?= htmlspecialchars($error) ?></div><?php endif; ?>

        <?php /* === ЦЕНЫ === */ if ($action === 'prices'): $prods = $pdo->query("SELECT * FROM products ORDER BY name")->fetchAll(); ?>
        <div class="glass-card">
            <div class="card-header">
                <div class="card-title">Управление ценами</div>
                <input type="text" id="searchP" class="input-modern" style="width:250px;" placeholder="Поиск товара...">
            </div>
            <div class="table-wrap">
                <table id="tblP"><tbody>
                <?php foreach($prods as $p): ?>
                <tr>
                    <td><?= htmlspecialchars($p['name']) ?></td>
                    <td style="text-align:right;">
                        <form method="POST" style="display:flex;gap:8px;justify-content:flex-end;align-items:center;">
                            <input type="hidden" name="csrf_token" value="<?= csrf_token() ?>">
                            <input type="hidden" name="id" value="<?= (int)$p['id'] ?>">
                            <input type="text" inputmode="numeric" name="price" value="<?= (int)$p['price'] ?>" class="input-modern" style="width:110px;text-align:right;">
                            <button name="save_price" class="btn btn-primary btn-sm"><i class="fas fa-check"></i></button>
                        </form>
                    </td>
                </tr>
                <?php endforeach; ?>
                </tbody></table>
            </div>
        </div>
        <script>document.getElementById('searchP').oninput=function(){let q=this.value.toLowerCase();document.querySelectorAll('#tblP tr').forEach(r=>r.style.display=r.textContent.toLowerCase().includes(q)?'':'none')}</script>

        <?php /* === ДАШБОРД === */ elseif ($action === 'dashboard'):
            $u=$pdo->query("SELECT COUNT(*) FROM users WHERE role='user'")->fetchColumn();
            $s=$pdo->query("SELECT COUNT(*) FROM users WHERE role='seller'")->fetchColumn();
            $o=$pdo->query("SELECT COUNT(*) FROM orders")->fetchColumn();
            $r=$pdo->query("SELECT SUM(total) FROM orders WHERE status != 'Отменён'")->fetchColumn() ?: 0;
        ?>
        <div class="stats-grid">
            <div class="stat-card"><div class="stat-icon purple"><i class="fas fa-users"></i></div><div class="stat-label">Клиенты</div><div class="stat-value"><?= $u ?></div></div>
            <div class="stat-card"><div class="stat-icon green"><i class="fas fa-user-tie"></i></div><div class="stat-label">Продавцы</div><div class="stat-value"><?= $s ?></div></div>
            <div class="stat-card"><div class="stat-icon orange"><i class="fas fa-shopping-bag"></i></div><div class="stat-label">Заказы</div><div class="stat-value"><?= $o ?></div></div>
            <div class="stat-card"><div class="stat-icon pink"><i class="fas fa-ruble-sign"></i></div><div class="stat-label">Выручка (без отмен)</div><div class="stat-value"><?= number_format($r,0,'',' ') ?> ₽</div></div>
        </div>
        <div class="glass-card">
            <div class="card-header"><div class="card-title">Последние заказы</div></div>
            <div class="table-wrap"><table class="m-cards">
                <thead><tr><th>№</th><th>Клиент</th><th>Сумма</th><th>Статус</th><th>Дата</th></tr></thead>
                <tbody>
                <?php foreach($pdo->query("SELECT o.*, u.name FROM orders o LEFT JOIN users u ON o.user_id=u.id ORDER BY o.created_at DESC LIMIT 5")->fetchAll() as $row): ?>
                <tr>
                    <td data-label="№">#<?= htmlspecialchars($row['order_number']) ?></td>
                    <td data-label="Клиент"><?= htmlspecialchars($row['customer_name']) ?></td>
                    <td data-label="Сумма" class="nowrap"><?= number_format($row['total'],0,'',' ') ?> ₽</td>
                    <td data-label="Статус"><span class="badge <?= $row['status']=='Новый'?'badge-new':($row['status']=='В обработке'?'badge-proc':($row['status']=='Доставлен'?'badge-done':'badge-cancel')) ?>"><?= htmlspecialchars($row['status']) ?></span></td>
                    <td data-label="Дата"><?= date('d.m.Y', strtotime($row['created_at'])) ?></td>
                </tr>
                <?php endforeach; ?>
                </tbody>
            </table></div>
        </div>

        <?php /* === АНАЛИТИКА === */ elseif ($action === 'stats'): $max=1; foreach($dailySales as $r) if($r['t']>$max) $max=$r['t']; ?>
        <div class="stats-grid">
            <div class="stat-card"><div class="stat-icon purple"><i class="fas fa-calendar-day"></i></div><div class="stat-label">Сегодня</div><div class="stat-value"><?= number_format($todayStats['t']??0,0,'',' ') ?> ₽</div></div>
            <div class="stat-card"><div class="stat-icon green"><i class="fas fa-calendar-week"></i></div><div class="stat-label">Неделя</div><div class="stat-value"><?= number_format($weekStats['t']??0,0,'',' ') ?> ₽</div></div>
            <div class="stat-card"><div class="stat-icon orange"><i class="fas fa-calendar-alt"></i></div><div class="stat-label">Месяц</div><div class="stat-value"><?= number_format($monthStats['t']??0,0,'',' ') ?> ₽</div></div>
        </div>
        <div class="glass-card">
            <div class="card-header"><div class="card-title">📈 Динамика за 7 дней</div></div>
            <div style="padding:24px;">
                <?php foreach($dailySales as $r): ?>
                <div class="chart-row">
                    <div class="chart-label"><?= date('d.m', strtotime($r['d'])) ?></div>
                    <div class="chart-track"><div class="chart-fill" style="width:<?= ($max>0 ? ($r['t']/$max*100) : 0) ?>%"></div></div>
                    <div class="chart-val"><?= number_format($r['t'],0,'',' ') ?></div>
                </div>
                <?php endforeach; ?>
            </div>
        </div>

        <?php /* === ТОВАРЫ === */ elseif ($action === 'products'): $prods = $pdo->query("SELECT * FROM products ORDER BY id DESC")->fetchAll(); ?>
        <div class="glass-card">
            <div class="card-header"><div class="card-title">Добавить товар</div></div>
            <form method="POST" class="form-grid">
                <input type="hidden" name="csrf_token" value="<?= csrf_token() ?>">
                <div class="input-group"><label>Название</label><input type="text" name="name" class="input-modern" required></div>
                <div class="input-group"><label>Цена (₽)</label><input type="text" inputmode="numeric" name="price" class="input-modern" placeholder="1500" required></div>
                <div class="input-group"><label>Категория</label>
                    <select name="category" class="input-modern">
                        <?php foreach($CATEGORIES_PREFERRED as $c): ?><option value="<?= htmlspecialchars($c) ?>"><?= htmlspecialchars($c) ?></option><?php endforeach; ?>
                    </select>
                </div>
                <div class="input-group"><label>Остаток</label><input type="number" name="stock" class="input-modern" value="0"></div>
                <div class="input-group"><label>Ссылка</label><input type="text" name="link" class="input-modern" placeholder="Glass.html"></div>
                <div class="input-group"><label>Фото URL</label><input type="text" name="img" class="input-modern"></div>
                <div class="input-group" style="justify-content:flex-end;"><button name="add_product" class="btn btn-primary">Добавить</button></div>
            </form>
        </div>
        <div class="glass-card">
            <div class="card-header"><div class="card-title">Все товары (<?= count($prods) ?>)</div></div>
            <div class="table-wrap"><table class="m-cards">
                <thead><tr><th>ID</th><th>Название</th><th>Цена</th><th>Остаток</th><th></th></tr></thead>
                <tbody>
                <?php foreach($prods as $p): ?>
                <tr>
                    <td class="td-block"><?= htmlspecialchars($p['name']) ?></td>
                    <td data-label="ID"><?= $p['id'] ?></td>
                    <td data-label="Цена" class="nowrap"><?= (int)$p['price'] ?> ₽</td>
                    <td data-label="Остаток"><?= $p['stock'] ?></td>
                    <td class="td-actions"><a href="?action=delete_product&id=<?= $p['id'] ?>" class="btn btn-danger btn-sm" onclick="return confirm('Удалить товар?')"><i class="fas fa-trash"></i></a></td>
                </tr>
                <?php endforeach; ?>
                </tbody>
            </table></div>
        </div>

        <?php /* === СКЛАД: списания и остатки === */ elseif ($action === 'inventory'): ?>
        <div class="glass-card">
            <div class="card-header">
                <div class="card-title">Остатки по ароматам</div>
                <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:center;">
                    <input type="text" id="searchI" class="input-modern" style="width:220px;" placeholder="Поиск товара...">
                    <form method="POST" onsubmit="return confirm('Обнулить ВСЕ остатки? Действие необратимо.');">
                        <input type="hidden" name="csrf_token" value="<?= csrf_token() ?>">
                        <button name="reset_stocks" class="btn btn-danger btn-sm"><i class="fas fa-eraser"></i> Обнулить</button>
                    </form>
                </div>
            </div>
            <form method="POST">
                <input type="hidden" name="csrf_token" value="<?= csrf_token() ?>">
                <div style="padding:20px 24px;">
                <?php foreach($cats as $cname):
                    $catProds=array_values(array_filter($prods_inv, function($p) use ($cname){ return $p['category']===$cname; }));
                    if (!$catProds) continue;
                    $catTotal=0; foreach($catProds as $p) $catTotal+=(int)$p['stock'];
                ?>
                <div class="inv-cat-block">
                    <div class="inv-cat" data-cat="<?= htmlspecialchars($cname) ?>">
                        <span class="inv-cat-name"><i class="fas fa-chevron-down inv-chev"></i> <?= htmlspecialchars($cname) ?></span>
                        <span class="inv-cat-total">в категории: <b class="cat-num"><?= $catTotal ?></b></span>
                    </div>
                    <div class="inv-cat-body">
                        <?php foreach($catProds as $p): ?>
                        <div class="inv-product" data-name="<?= mb_strtolower(htmlspecialchars($p['name'])) ?>">
                            <div class="inv-product-name">
                                <?= htmlspecialchars($p['name']) ?>
                                <span class="inv-total">всего: <?= (int)$p['stock'] ?></span>
                            </div>
                            <?php if($p['variants']): ?>
                                <?php foreach($p['variants'] as $v): ?>
                                <div class="inv-row">
                                    <span class="inv-flavor"><b><?= htmlspecialchars($v['flavor_name']) ?></b></span>
                                    <div class="inv-controls">
                                        <button type="button" class="inv-step minus" data-step="-1" title="Списание (продажа)">−</button>
                                        <input type="number" name="variants[<?= $v['id'] ?>]" value="<?= (int)$v['stock'] ?>" class="input-modern inv-input" min="0">
                                        <button type="button" class="inv-step plus" data-step="1" title="Приход">+</button>
                                    </div>
                                </div>
                                <?php endforeach; ?>
                            <?php else: ?>
                                <div class="inv-row">
                                    <span class="inv-flavor">Остаток (без ароматов)</span>
                                    <div class="inv-controls">
                                        <button type="button" class="inv-step minus" data-step="-1" title="Списание (продажа)">−</button>
                                        <input type="number" name="products[<?= $p['id'] ?>]" value="<?= (int)$p['stock'] ?>" class="input-modern inv-input" min="0">
                                        <button type="button" class="inv-step plus" data-step="1" title="Приход">+</button>
                                    </div>
                                </div>
                            <?php endif; ?>
                        </div>
                        <?php endforeach; ?>
                    </div>
                </div>
                <?php endforeach; ?>

                <?php if(!empty($noCat)): ?>
                <div class="inv-cat-block">
                    <div class="inv-cat" data-cat="Без категории">
                        <span class="inv-cat-name"><i class="fas fa-chevron-down inv-chev"></i> Без категории</span>
                        <span class="inv-cat-total">в категории: <b class="cat-num">0</b></span>
                    </div>
                    <div class="inv-cat-body">
                        <?php foreach($noCat as $p): ?>
                        <div class="inv-product" data-name="<?= mb_strtolower(htmlspecialchars($p['name'])) ?>">
                            <div class="inv-product-name">
                                <?= htmlspecialchars($p['name']) ?>
                                <span class="inv-total">всего: <?= (int)$p['stock'] ?></span>
                            </div>
                            <div class="inv-row">
                                <span class="inv-flavor">Остаток</span>
                                <div class="inv-controls">
                                    <button type="button" class="inv-step minus" data-step="-1">−</button>
                                    <input type="number" name="products[<?= $p['id'] ?>]" value="<?= (int)$p['stock'] ?>" class="input-modern inv-input" min="0">
                                    <button type="button" class="inv-step plus" data-step="1">+</button>
                                </div>
                            </div>
                        </div>
                        <?php endforeach; ?>
                    </div>
                </div>
                <?php endif; ?>
                </div>
                <div style="padding:20px;text-align:right;border-top:1px solid var(--border);">
                    <button name="mass_update" class="btn btn-primary"><i class="fas fa-save"></i> Сохранить всё</button>
                </div>
            </form>
        </div>
        <script>
        document.getElementById('searchI').oninput=function(){
            var q=this.value.toLowerCase();
            var products=document.querySelectorAll('.inv-product');
            for(var i=0;i<products.length;i++){
                products[i].style.display=products[i].dataset.name.includes(q)?'':'none';
            }
            if(q){
                var blocks=document.querySelectorAll('.inv-cat-block');
                for(var j=0;j<blocks.length;j++) blocks[j].classList.add('open');
            }
        };

        var INV_KEY='inv_open_cats';
        function invGetOpen(){ try{ return JSON.parse(localStorage.getItem(INV_KEY)||'{}'); }catch(e){ return {}; } }
        function invSetOpen(o){ try{ localStorage.setItem(INV_KEY, JSON.stringify(o)); }catch(e){} }
        var catHeaders=document.querySelectorAll('.inv-cat');
        for(var k=0;k<catHeaders.length;k++){
            (function(h){
                var block=h.parentElement, name=h.dataset.cat||'';
                if(invGetOpen()[name]) block.classList.add('open');
                h.addEventListener('click', function(){
                    block.classList.toggle('open');
                    var o=invGetOpen(); o[name]=block.classList.contains('open'); invSetOpen(o);
                });
            })(catHeaders[k]);
        }

        document.addEventListener('click', function(e){
            var b=e.target.closest('.inv-step'); if(!b) return;
            var input=b.parentElement.querySelector('input');
            var v=parseInt(input.value||'0',10)||0;
            v+=parseInt(b.dataset.step,10); if(v<0) v=0;
            input.value=v;
            recalcAll();
        });

        function recalcAll(){
            var products=document.querySelectorAll('.inv-product');
            for(var p=0;p<products.length;p++){
                var card=products[p];
                var s=0;
                var inputs=card.querySelectorAll('input[type="number"]');
                for(var i=0;i<inputs.length;i++) s+=+inputs[i].value||0;
                var t=card.querySelector('.inv-total'); if(t) t.textContent='всего: '+s;
                card.dataset.total=s;
            }
            var blocks=document.querySelectorAll('.inv-cat-block');
            for(var b=0;b<blocks.length;b++){
                var bl=blocks[b];
                var s=0;
                var prods=bl.querySelectorAll('.inv-product');
                for(var pp=0;pp<prods.length;pp++) s+=+(prods[pp].dataset.total||0);
                var num=bl.querySelector('.cat-num'); if(num) num.textContent=s;
            }
        }
        document.addEventListener('input', function(e){
            if (e.target.matches('.inv-product input[type="number"]')) recalcAll();
        });
        recalcAll();
        </script>

        <?php /* === СКЛАДЫ ПРОДАВЦОВ === */ elseif ($action === 'seller_stock'): ?>
        <div class="glass-card" style="margin-bottom:24px;">
            <div style="padding:20px 24px;">
                <form method="GET" style="display:flex;gap:15px;align-items:center;flex-wrap:wrap;">
                    <input type="hidden" name="action" value="seller_stock">
                    <label style="font-weight:600;color:var(--primary);">Продавец:</label>
                    <select name="seller_id" onchange="this.form.submit()" class="input-modern" style="width:250px;">
                        <?php foreach($sellers as $s): ?><option value="<?= $s['id'] ?>" <?= $selected_seller==$s['id']?'selected':'' ?>><?= htmlspecialchars($s['name']) ?></option><?php endforeach; ?>
                    </select>
                </form>
            </div>
        </div>
        <?php if(!empty($sellers)): ?>
        <div class="glass-card">
            <form method="POST">
                <input type="hidden" name="csrf_token" value="<?= csrf_token() ?>">
                <input type="hidden" name="seller_id" value="<?= $selected_seller ?>">
                <div class="table-wrap"><table class="m-cards">
                    <thead><tr><th>Товар</th><th>Аромат</th><th>Цена</th><th>Остаток</th></tr></thead>
                    <tbody>
                    <?php foreach($products_ss as $p): $d=$p['default_variant']; ?>
                    <tr>
                        <td class="td-block"><?= htmlspecialchars($p['name']) ?></td>
                        <td data-label="Аромат">
                            <?php if($p['variants']): ?>
                                <select class="input-modern" id="fl-<?= $p['product_id'] ?>" onchange="updV('<?= $p['product_id'] ?>',this)">
                                    <?php foreach($p['variants'] as $v): ?><option value="<?= $v['id'] ?>" data-p="<?= $v['variant_price']?:$v['product_price'] ?>" data-s="<?= $v['seller_stock'] ?>" <?= $d&&$v['id']==$d['id']?'selected':'' ?>><?= htmlspecialchars($v['flavor_name']) ?></option><?php endforeach; ?>
                                </select>
                            <?php else: ?>
                                <span style="color:var(--muted);font-size:.8rem;">нет ароматов</span>
                            <?php endif; ?>
                        </td>
                        <td data-label="Цена" class="nowrap" id="pr-<?= $p['product_id'] ?>"><?= number_format($d?($d['variant_price']?:$d['product_price']):$p['price'],0,'',' ') ?> ₽</td>
                        <td data-label="Остаток"><input type="number" name="stocks[<?= $p['product_id'] ?>|<?= $d?$d['id']:'' ?>]" id="st-<?= $p['product_id'] ?>" value="<?= $d?$d['seller_stock']:0 ?>" class="input-modern" style="width:80px;"></td>
                    </tr>
                    <?php endforeach; ?>
                    </tbody>
                </table></div>
                <div style="padding:20px;text-align:right;"><button name="mass_update" class="btn btn-primary">Сохранить склад</button></div>
            </form>
        </div>
        <script>function updV(id,s){var o=s.options[s.selectedIndex];document.getElementById('pr-'+id).textContent=Number(o.dataset.p).toLocaleString()+' ₽';var i=document.getElementById('st-'+id);i.value=o.dataset.s;i.name='stocks['+id+'|'+s.value+']';}</script>
        <?php endif; ?>

        <?php /* === ПРОДАВЦЫ === */ elseif ($action === 'sellers'): $sels = $pdo->query("SELECT * FROM users WHERE role='seller'")->fetchAll(); ?>
        <div class="glass-card">
            <div class="card-header"><div class="card-title">Добавить продавца</div></div>
            <form method="POST" class="form-grid">
                <input type="hidden" name="csrf_token" value="<?= csrf_token() ?>">
                <div class="input-group"><label>Имя</label><input type="text" name="name" class="input-modern" required></div>
                <div class="input-group"><label>Email</label><input type="email" name="email" class="input-modern" required></div>
                <div class="input-group"><label>Пароль</label><input type="text" name="password" value="seller123" class="input-modern"></div>
                <div class="input-group" style="justify-content:flex-end;"><button name="add_seller" class="btn btn-primary">Добавить</button></div>
            </form>
        </div>
        <div class="glass-card">
            <div class="table-wrap"><table class="m-cards">
                <thead><tr><th>Имя</th><th>Email</th><th>Дата</th><th></th></tr></thead>
                <tbody>
                <?php foreach($sels as $s): ?>
                <tr>
                    <td class="td-block"><?= htmlspecialchars($s['name']) ?></td>
                    <td data-label="Email" class="break"><?= htmlspecialchars($s['email']) ?></td>
                    <td data-label="Дата"><?= date('d.m.Y',strtotime($s['created_at'])) ?></td>
                    <td class="td-actions"><a href="?action=delete_seller&id=<?= $s['id'] ?>" class="btn btn-danger btn-sm" onclick="return confirm('Удалить продавца?')"><i class="fas fa-trash"></i></a></td>
                </tr>
                <?php endforeach; ?>
                </tbody>
            </table></div>
        </div>

        <?php /* === ЗАКАЗЫ === */ elseif ($action === 'orders'): $ords = $pdo->query("SELECT o.*, u.name FROM orders o LEFT JOIN users u ON o.user_id=u.id ORDER BY o.created_at DESC")->fetchAll(); ?>
        <div class="glass-card">
            <div class="table-wrap"><table class="m-cards">
                <thead><tr><th>№</th><th>Клиент</th><th>Сумма</th><th>Статус</th><th>Действие</th></tr></thead>
                <tbody>
                <?php foreach($ords as $o): ?>
                <tr>
                    <td class="td-block">#<?= htmlspecialchars($o['order_number']) ?></td>
                    <td data-label="Клиент"><?= htmlspecialchars($o['customer_name']) ?></td>
                    <td data-label="Сумма" class="nowrap"><?= number_format($o['total'],0,'',' ') ?> ₽</td>
                    <td data-label="Статус"><span class="badge <?= $o['status']=='Новый'?'badge-new':($o['status']=='В обработке'?'badge-proc':($o['status']=='Доставлен'?'badge-done':'badge-cancel')) ?>"><?= htmlspecialchars($o['status']) ?></span></td>
                    <td data-label="Действие">
                        <select class="input-modern" style="padding:8px 12px;" onchange="location.href='?action=update_order_status&order_id=<?= $o['id'] ?>&status='+encodeURIComponent(this.value)">
                            <?php foreach(['Новый','В обработке','Доставлен','Отменён'] as $st): ?><option <?= $o['status']==$st?'selected':'' ?>><?= $st ?></option><?php endforeach; ?>
                        </select>
                    </td>
                </tr>
                <?php endforeach; ?>
                </tbody>
            </table></div>
        </div>

        <?php /* === КЛИЕНТЫ === */ elseif ($action === 'users'): $usrs = $pdo->query("SELECT u.*, (SELECT COUNT(*) FROM orders WHERE user_id=u.id) c FROM users u ORDER BY u.created_at DESC")->fetchAll(); ?>
        <div class="glass-card">
            <div class="table-wrap"><table class="m-cards">
                <thead><tr><th>Имя</th><th>Email</th><th>Возвраты</th><th>Заказы</th><th></th></tr></thead>
                <tbody>
                <?php foreach($usrs as $u): ?>
                <tr>
                    <td class="td-block"><?= htmlspecialchars($u['name']) ?></td>
                    <td data-label="Email" class="break"><?= htmlspecialchars($u['email']) ?></td>
                    <td data-label="Возвраты"><?= $u['returns'] ?>/5</td>
                    <td data-label="Заказы"><?= $u['c'] ?></td>
                    <td class="td-actions">
                        <?php if($u['role']!='admin'): ?>
                        <a href="?action=add_return&user_id=<?= $u['id'] ?>" class="btn btn-primary btn-sm" style="margin-right:5px;" title="Добавить возврат"><i class="fas fa-plus"></i></a>
                        <a href="?action=delete_user&user_id=<?= $u['id'] ?>" class="btn btn-danger btn-sm" onclick="return confirm('Удалить клиента?')"><i class="fas fa-trash"></i></a>
                        <?php endif; ?>
                    </td>
                </tr>
                <?php endforeach; ?>
                </tbody>
            </table></div>
        </div>

        <?php /* === ВОЗВРАТЫ === */ elseif ($action === 'returns'): $rets = $pdo->query("SELECT r.*, u.name FROM returns r JOIN users u ON r.user_id=u.id ORDER BY r.return_date DESC")->fetchAll(); ?>
        <div class="glass-card">
            <div class="table-wrap"><table class="m-cards">
                <thead><tr><th>Клиент</th><th>Дата</th><th>Статус</th></tr></thead>
                <tbody>
                <?php foreach($rets as $r): ?>
                <tr>
                    <td class="td-block"><?= htmlspecialchars($r['name']) ?></td>
                    <td data-label="Дата"><?= date('d.m.Y H:i',strtotime($r['return_date'])) ?></td>
                    <td data-label="Статус"><span class="badge badge-done"><?= htmlspecialchars($r['status']) ?></span></td>
                </tr>
                <?php endforeach; ?>
                </tbody>
            </table></div>
        </div>

        <?php /* === БЕЗОПАСНОСТЬ === */ elseif ($action === 'password'): ?>
        <div class="glass-card" style="max-width:500px;">
            <div class="card-header"><div class="card-title">Смена пароля</div></div>
            <form method="POST" style="padding:24px;display:flex;flex-direction:column;gap:20px;">
                <input type="hidden" name="csrf_token" value="<?= csrf_token() ?>">
                <div class="input-group"><label>Текущий пароль</label><input type="password" name="old_password" class="input-modern" required></div>
                <div class="input-group"><label>Новый пароль</label><input type="password" name="new_password" class="input-modern" required minlength="8"></div>
                <div class="input-group"><label>Подтверждение</label><input type="password" name="confirm_password" class="input-modern" required></div>
                <button name="change_password" class="btn btn-primary" style="margin-top:10px;">Обновить пароль</button>
            </form>
        </div>
        <?php endif; ?>

    </main>
</div>

<script>
(function(){
    var sb = document.getElementById('sidebar');
    var ov = document.getElementById('sidebarOverlay');
    var tg = document.getElementById('mobileToggle');

    function closeM(){
        sb.classList.remove('open');
        ov.classList.remove('active');
        document.body.style.overflow = '';
    }
    function openM(){
        sb.classList.add('open');
        ov.classList.add('active');
        document.body.style.overflow = 'hidden';
    }

    tg.addEventListener('click', function(){
        sb.classList.contains('open') ? closeM() : openM();
    });
    ov.addEventListener('click', closeM);
})();

var al = document.getElementById('alertSuccess');
if(al){
    setTimeout(function(){
        al.style.transition = 'opacity .5s';
        al.style.opacity = '0';
        setTimeout(function(){ al.remove(); }, 500);
    }, 3000);
}
</script>
</body>
</html>