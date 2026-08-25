<?php
session_start();
require_once 'config.php';
header('Content-Type: application/json; charset=utf-8');

$uid = isset($_SESSION['user_id']) ? (int)$_SESSION['user_id'] : 0;

if (!$uid) {
    echo json_encode(['authenticated' => false]);
    exit;
}

$stmt = $pdo->prepare("SELECT id, name, email, phone, returns, created_at, role FROM users WHERE id = ?");
$stmt->execute([$uid]);
$user = $stmt->fetch(PDO::FETCH_ASSOC);

// В личный кабинет клиента пускаем ТОЛЬКО роль user (админ и продавцы — мимо)
if (!$user || $user['role'] !== 'user') {
    echo json_encode(['authenticated' => false]);
    exit;
}

// Сохранение профиля
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $name  = trim($_POST['name'] ?? '');
    $phone = trim($_POST['phone'] ?? '');

    if ($name === '') {
        echo json_encode(['ok' => false, 'message' => 'Укажите имя']);
        exit;
    }

    try {
        $pdo->prepare("UPDATE users SET name = ?, phone = ? WHERE id = ?")
            ->execute([mb_substr($name, 0, 100), mb_substr($phone, 0, 30), $uid]);
    } catch (Exception $e) {
        $pdo->prepare("UPDATE users SET name = ? WHERE id = ?")
            ->execute([mb_substr($name, 0, 100), $uid]);
    }

    echo json_encode(['ok' => true]);
    exit;
}

unset($user['role']);

$ret = $pdo->prepare("SELECT status, return_date FROM returns WHERE user_id = ? ORDER BY return_date DESC");
$ret->execute([$uid]);

echo json_encode([
    'authenticated'   => true,
    'user'            => $user,
    'returns_history' => $ret->fetchAll(PDO::FETCH_ASSOC)
]);