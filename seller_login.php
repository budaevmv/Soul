<?php
session_start();
require_once 'config.php';

if (isset($_SESSION['role']) && $_SESSION['role'] === 'seller') {
    header('Location: seller_dashboard.php');
    exit;
}

$error = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $email = trim($_POST['email'] ?? '');
    $password = $_POST['password'] ?? '';
    
    $stmt = $pdo->prepare("SELECT * FROM users WHERE email = ? AND role = 'seller'");
    $stmt->execute([$email]);
    $user = $stmt->fetch();
    
    if ($user && password_verify($password, $user['password'])) {
        $_SESSION['user_id'] = $user['id'];
        $_SESSION['user_name'] = $user['name'];
        $_SESSION['user_email'] = $user['email'];
        $_SESSION['role'] = $user['role'];
        header('Location: seller_dashboard.php');
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
    <title>Вход для продавца | Soul Candles</title>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&family=Playfair+Display:wght@400;500;600&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0-beta3/css/all.min.css">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Inter', sans-serif;
            background: linear-gradient(135deg, #2D5A44 0%, #1E3A2F 100%);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .login-container {
            background: #fff;
            border-radius: 32px;
            padding: 40px;
            width: 100%;
            max-width: 420px;
            box-shadow: 0 20px 40px rgba(0,0,0,0.2);
        }
        .logo {
            font-family: 'Playfair Display', serif;
            font-size: 2rem;
            text-align: center;
            color: #8C6E5A;
            margin-bottom: 10px;
        }
        .logo span { color: #1E1E1E; font-weight: 300; }
        .subtitle {
            text-align: center;
            color: #aaa;
            margin-bottom: 30px;
            font-size: 0.85rem;
        }
        .input-group { margin-bottom: 20px; }
        label { display: block; margin-bottom: 8px; font-weight: 500; font-size: 0.85rem; }
        input {
            width: 100%;
            padding: 14px 16px;
            border: 1px solid #ddd;
            border-radius: 12px;
            font-family: inherit;
            font-size: 1rem;
            transition: border-color 0.3s;
        }
        input:focus { outline: none; border-color: #8C6E5A; }
        .btn {
            width: 100%;
            background: #8C6E5A;
            color: white;
            border: none;
            padding: 14px;
            border-radius: 999px;
            font-size: 1rem;
            font-weight: 500;
            cursor: pointer;
            transition: background 0.3s;
        }
        .btn:hover { background: #1E3A2F; }
        .error {
            background: #FFEBEE;
            color: #C62828;
            padding: 12px;
            border-radius: 12px;
            margin-bottom: 20px;
            text-align: center;
            font-size: 0.85rem;
        }
        .back-link {
            text-align: center;
            margin-top: 20px;
        }
        .back-link a {
            color: #8C6E5A;
            text-decoration: none;
            font-size: 0.85rem;
        }
        .back-link a:hover { text-decoration: underline; }
    </style>
</head>
<body>
    <div class="login-container">
        <div class="logo">Soul <span>Candles</span></div>
        <div class="subtitle">Вход для продавца</div>
        
        <?php if($error): ?>
            <div class="error"><?= htmlspecialchars($error) ?></div>
        <?php endif; ?>
        
        <form method="POST">
            <div class="input-group">
                <label>Email</label>
                <input type="email" name="email" required placeholder="Введите email" autofocus>
            </div>
            <div class="input-group">
                <label>Пароль</label>
                <input type="password" name="password" required placeholder="Введите пароль">
            </div>
            <button type="submit" class="btn">Войти</button>
        </form>
        
        <div class="back-link">
            <a href="index.html">← Вернуться на сайт</a>
        </div>
    </div>
</body>
</html>