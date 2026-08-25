<?php
session_start();
require_once 'config.php';

if (isset($_SESSION['user_id']) && ($_SESSION['role'] ?? '') === 'user') {
    header('Location: account.html');
    exit;
}

$error = '';
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $name     = trim($_POST['name'] ?? '');
    $email    = trim($_POST['email'] ?? '');
    $phone    = trim($_POST['phone'] ?? '');
    $password = $_POST['password'] ?? '';
    $confirm  = $_POST['confirm'] ?? '';

    if ($name === '') {
        $error = 'Укажите имя';
    } elseif (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        $error = 'Некорректный email';
    } elseif (strlen($password) < 6) {
        $error = 'Пароль — минимум 6 символов';
    } elseif ($password !== $confirm) {
        $error = 'Пароли не совпадают';
    } else {
        try {
            $stmt = $pdo->prepare("INSERT INTO users (name, email, phone, password, role, returns) VALUES (?,?,?,?, 'user', 0)");
            $stmt->execute([$name, $email, $phone, password_hash($password, PASSWORD_DEFAULT)]);

            $_SESSION['user_id']    = (int)$pdo->lastInsertId();
            $_SESSION['user_name']  = $name;
            $_SESSION['user_email'] = $email;
            $_SESSION['role']       = 'user';

            header('Location: account.html');
            exit;
        } catch (PDOException $e) {
            $error = (isset($e->errorInfo[1]) && $e->errorInfo[1] == 1062)
                ? 'Пользователь с таким email уже существует'
                : 'Ошибка регистрации, попробуйте позже';
        }
    }
}
?>
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Регистрация | Soul Candles</title>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&family=Playfair+Display:wght@400;600&display=swap" rel="stylesheet">
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Inter',sans-serif;background:linear-gradient(135deg,#1E3A2F 0%,#2D5A44 100%);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}
.box{background:#fff;border-radius:32px;padding:40px;width:100%;max-width:420px;box-shadow:0 20px 40px rgba(0,0,0,.25)}
.logo{font-family:'Playfair Display',serif;font-size:2rem;text-align:center;color:#8C6E5A;margin-bottom:8px}
.logo span{color:#1E1E1E;font-weight:300}
.subtitle{text-align:center;color:#aaa;margin-bottom:28px;font-size:.85rem}
.group{margin-bottom:16px}
.group label{display:block;margin-bottom:8px;font-weight:500;font-size:.85rem}
.group input{width:100%;padding:13px 16px;border:1px solid #ddd;border-radius:12px;font-family:inherit;font-size:1rem}
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
    <div class="subtitle">Регистрация в клубе Soul Candles</div>

    <?php if ($error): ?><div class="error"><?= htmlspecialchars($error) ?></div><?php endif; ?>

    <form method="POST" autocomplete="off">
        <div class="group">
            <label>Имя</label>
            <input type="text" name="name" required placeholder="Как к вам обращаться?">
        </div>
        <div class="group">
            <label>Email</label>
            <input type="email" name="email" required placeholder="Ваш email">
        </div>
        <div class="group">
            <label>Телефон</label>
            <input type="tel" name="phone" placeholder="+7 (___) ___-__-__">
        </div>
        <div class="group">
            <label>Пароль</label>
            <input type="password" name="password" required placeholder="Минимум 6 символов">
        </div>
        <div class="group">
            <label>Повторите пароль</label>
            <input type="password" name="confirm" required placeholder="Ещё раз">
        </div>
        <button type="submit" class="btn">Создать аккаунт</button>
    </form>

    <div class="links">
        Уже есть аккаунт? <a href="user_login.php">Войти</a><br><br>
        <a href="index.html">← Вернуться на сайт</a>
    </div>
</div>
</body>
</html>