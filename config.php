<?php
if (session_status() === PHP_SESSION_NONE) session_start();

// В идеале вынести в .env, но для совместимости оставим здесь
$host = 'localhost';
$dbname = 'u3454579_Order'; 
$username = 'u3454579_Rimma'; 
$password = 'xL9pN7nC9viK4pL1'; 

try {
    $pdo = new PDO("mysql:host=$host;dbname=$dbname;charset=utf8mb4", $username, $password);
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
} catch(PDOException $e) {
    error_log("DB Error: " . $e->getMessage());
    die("Ошибка соединения с БД");
}

function isAdmin() { return isset($_SESSION['role']) && $_SESSION['role'] === 'admin'; }
function isSeller() { return isset($_SESSION['role']) && $_SESSION['role'] === 'seller'; }
function isLoggedIn() { return isset($_SESSION['user_id']); }

// CSRF Защита
function csrf_token() {
    if (empty($_SESSION['csrf_token'])) $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
    return $_SESSION['csrf_token'];
}
function verify_csrf() {
    return isset($_POST['csrf_token']) && hash_equals($_SESSION['csrf_token'], $_POST['csrf_token']);
}
?>