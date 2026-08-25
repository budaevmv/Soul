<?php
session_start();
require_once 'config.php';
header('Content-Type: application/json');

if (!isLoggedIn()) {
    echo json_encode(['success' => false, 'error' => 'Не авторизован']);
    exit;
}

$data = json_decode(file_get_contents('php://input'), true);
if (!$data) {
    echo json_encode(['success' => false, 'error' => 'Нет данных']);
    exit;
}

$userId = $_SESSION['user_id'];               // ← берём ТОЛЬКО из сессии
$name   = htmlspecialchars(trim($data['name'] ?? ''));
$phone  = htmlspecialchars(trim($data['phone'] ?? ''));

if (!$name) {
    echo json_encode(['success' => false, 'error' => 'Имя обязательно']);
    exit;
}

try {
    $stmt = $pdo->prepare("UPDATE users SET name = ?, phone = ? WHERE id = ?");
    $stmt->execute([$name, $phone, $userId]);
    $_SESSION['user_name'] = $name;
    echo json_encode(['success' => true]);
} catch (Exception $e) {
    echo json_encode(['success' => false, 'error' => $e->getMessage()]);
}
?>