<?php
session_start();                              // ← чтобы видеть, кто заказывает
require_once 'config.php';
header('Content-Type: application/json');

// ========== НАСТРОЙКИ ==========
$ADMIN_EMAIL = 'corporate@soulcandles.ru';
$BOT_TOKEN   = '8746669124:AAFDZV2Q6ZRGzeA9zF4kEvdesBn017MMLic'; // TODO: вынести в .env

// Получатели Telegram: только личные чаты (без группы)
$CHAT_IDS = [
    '43545744',   // Max
    '698149118',  // Римма
];
// ================================

$data = json_decode(file_get_contents('php://input'), true);
if (!$data) {
    echo json_encode(['success' => false, 'error' => 'Нет данных']);
    exit;
}

$items = $data['items'] ?? [];
$total = (int)($data['total'] ?? 0);
$date  = date('d.m.Y H:i:s');
$name  = htmlspecialchars($data['customer_name'] ?? $data['name'] ?? '');
$phone = htmlspecialchars($data['customer_phone'] ?? $data['phone'] ?? '');

// Email берём из сессии (если клиент вошёл) или из данных корзины
$email = $_SESSION['user_email'] ?? filter_var($data['customer_email'] ?? '', FILTER_VALIDATE_EMAIL) ?: '';

if (!$name || !$phone) {
    echo json_encode(['success' => false, 'error' => 'Имя и телефон обязательны']);
    exit;
}

// ---------- Привязка заказа к аккаунту ----------
$user_id = null;
if ($email) {
    $st = $pdo->prepare("SELECT id FROM users WHERE email = ?");
    $st->execute([$email]);
    $user_id = $st->fetchColumn() ?: null;
}

// ---------- Текст для Telegram ----------
$orderText = "🕯️ НОВЫЙ ЗАКАЗ Soul Candles\n"
           . "📅 $date\n"
           . "👤 $name\n"
           . "📞 $phone\n"
           . ($email ? "📧 $email\n" : "")
           . "━━━━━━━━━━\n";

foreach ($items as $item) {
    $q = (int)($item['qty'] ?? $item['quantity'] ?? 1);
    $p = (int)($item['price'] ?? 0);
    $orderText .= "• " . ($item['name'] ?? 'Товар') . " — $q шт. × $p ₽ = " . ($q * $p) . " ₽\n";
}

$orderText .= "━━━━━━━━━━\n💰 ИТОГО: $total ₽";

$success = true;
$errors  = [];

// ---------- Функция отправки в Telegram ----------
function sendTelegramMessage(string $token, string $chatId, string $text): array
{
    $ch = curl_init("https://api.telegram.org/bot{$token}/sendMessage");

    curl_setopt_array($ch, [
        CURLOPT_POST           => true,
        CURLOPT_POSTFIELDS     => [
            'chat_id' => $chatId,
            'text'    => $text,
        ],
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT        => 10,
    ]);

    $response  = curl_exec($ch);
    $httpCode  = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $curlError = curl_error($ch);

    curl_close($ch);

    return [
        'ok'         => $httpCode === 200,
        'http_code'  => $httpCode,
        'response'   => $response,
        'curl_error' => $curlError,
    ];
}

// ---------- Отправка всем получателям ----------
$telegramSent      = false;
$telegramErrors    = [];
$telegramDelivered = [];

$CHAT_IDS = array_unique(array_filter(array_map('trim', $CHAT_IDS)));

foreach ($CHAT_IDS as $chatId) {
    $result = sendTelegramMessage($BOT_TOKEN, (string)$chatId, $orderText);

    if ($result['ok']) {
        $telegramDelivered[] = $chatId;
    } else {
        $detail = $result['response'] ?: $result['curl_error'];
        $telegramErrors[] = "$chatId => $detail";
    }
}

// Успешно только если дошло ВСЕМ получателям
$telegramSent = count($telegramDelivered) === count($CHAT_IDS);

if (!$telegramSent) {
    $success = false;
    $errors[] = 'Telegram: ' . implode(' | ', $telegramErrors);
}

// ---------- Сохранение в БД ----------
$dbSaved     = false;
$orderNumber = 'SC-' . date('ymd') . '-' . rand(1000, 9999);

try {
    $stmt = $pdo->prepare(
        "INSERT INTO orders (order_number, user_id, customer_name, customer_phone, items, total, status, created_at) 
         VALUES (?, ?, ?, ?, ?, ?, 'Новый', NOW())"
    );
    $stmt->execute([
        $orderNumber,
        $user_id,
        $name,
        $phone,
        json_encode($items, JSON_UNESCAPED_UNICODE),
        $total
    ]);
    $dbSaved = true;
} catch (Exception $e) {
    $success = false;
    $errors[] = 'БД: ' . $e->getMessage();
}

// ---------- Email админу ----------
$mailHeaders = "MIME-Version: 1.0\r\n"
             . "Content-Type: text/html; charset=UTF-8\r\n"
             . "From: Soul Candles <noreply@soulcandles.ru>\r\n";

$mailSent = mail(
    $ADMIN_EMAIL,
    "🕯️ Новый заказ от $name",
    nl2br(htmlspecialchars($orderText)),
    $mailHeaders
);

// ---------- Ответ ----------
echo json_encode([
    'success'       => $success || $dbSaved,
    'order_number'  => $orderNumber,
    'telegram_sent' => $telegramSent,
    'db_saved'      => $dbSaved,
    'mail_sent'     => (bool)$mailSent,
    'error'         => $errors ? implode(', ', $errors) : null
], JSON_UNESCAPED_UNICODE);
?>