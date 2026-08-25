<?php
session_start();
require_once 'config.php';

if (isset($_SESSION['user_id']) && ($_SESSION['role'] ?? '') === 'user') {
    header('Location: account.html');
    exit;
}

$error = '';
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $email    = trim($_POST['email'] ?? '');
    $password = $_POST['password'] ?? '';

    $stmt = $pdo->prepare("SELECT * FROM users WHERE email = ? AND role = 'user'");
    $stmt->execute([$email]);
    $user = $stmt->fetch();

    if ($user && password_verify($password, $user['password'])) {
        $_SESSION['user_id']    = $user['id'];
        $_SESSION['user_name']  = $user['name'];
        $_SESSION['user_email'] = $user['email'];
        $_SESSION['role']       = $user['role'];
        header('Location: account.html');
        exit;
    } else {
        $error = 'Неверный email или пароль';
    }
}
?>
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Вход | Soul Candles</title>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&family=Playfair+Display:wght@400;600&display=swap" rel="stylesheet">
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Inter',sans-serif;background:linear-gradient(135deg,#1E3A2F 0%,#2D5A44 100%);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}
.box{background:#fff;border-radius:32px;padding:40px;width:100%;max-width:420px;box-shadow:0 20px 40px rgba(0,0,0,.25)}
.logo{font-family:'Playfair Display',serif;font-size:2rem;text-align:center;color:#8C6E5A;margin-bottom:8px}
.logo span{color:#1E1E1E;font-weight:300}
.subtitle{text-align:center;color:#aaa;margin-bottom:28px;font-size:.85rem}
.group{margin-bottom:18px}
.group label{display:block;margin-bottom:8px;font-weight:500;font-size:.85rem}
.group input{width:100%;padding:14px 16px;border:1px solid #ddd;border-radius:12px;font-family:inherit;font-size:1rem}
.group input:focus{outline:none;border-color:#8C6E5A}
.btn{width:100%;background:#8C6E5A;color:#fff;border:none;padding:14px;border-radius:999px;font-size:1rem;font-weight:500;cursor:pointer;transition:background .3s}
.btn:hover{background:#1E3A2F}
.error{background:#FFEBEE;color:#C62828;padding:12px;border-radius:12px;margin-bottom:18px;text-align:center;font-size:.85rem}
.links{text-align:center;margin-top:20px;font-size:.85rem}
.links a{color:#8C6E5A;text-decoration:none}
.links a:hover{text-decoration:underline}
</style>
</head>
<body>
<div class="box">
    <div class="logo">Soul <span>Candles</span></div>
    <div class="subtitle">Вход в личный кабинет</div>

    <?php if ($error): ?><div class="error"><?= htmlspecialchars($error) ?></div><?php endif; ?>

    <form method="POST" autocomplete="off">
        <div class="group">
            <label>Email</label>
            <input type="email" name="email" required placeholder="Ваш email">
        </div>
        <div class="group">
            <label>Пароль</label>
            <input type="password" name="password" required placeholder="Ваш пароль">
        </div>
        <button type="submit" class="btn">Войти</button>
    </form>

    <div class="links">
        Нет аккаунта? <a href="user_register.php">Зарегистрироваться</a><br><br>
        <a href="index.html">← Вернуться на сайт</a>
    </div>
</div>
</body>
</html>