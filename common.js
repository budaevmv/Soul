// ============================================
// ОБЩИЙ МОДУЛЬ SOUL CANDLES
// ============================================
(function() {
'use strict';
var CART_KEY = 'soulCandlesCart';
var cart = JSON.parse(localStorage.getItem(CART_KEY) || '[]');
var priceMap = {};

function getCart() { return cart; }

function saveCart() {
    localStorage.setItem(CART_KEY, JSON.stringify(cart));
    updateCartCount();
    renderCartModal();
}

function updateCartCount() {
    var count = 0;
    for (var i = 0; i < cart.length; i++) {
        count += cart[i].quantity;
    }
    var el = document.getElementById('cart-count');
    if (el) el.textContent = count;
}

function addToCart(product) {
    // УБРАТЬ проверку на price === 0!
    // if (!product.price || product.price === 0) { ... }
    
    var uniqueId = product.id + '_' + (product.variant || 'default');
    
    var existing = null;
    for (var i = 0; i < cart.length; i++) {
        if (cart[i].id === uniqueId) {
            existing = cart[i];
            break;
        }
    }

    if (existing) {
        existing.quantity++;
    } else {
        cart.push({ 
            id: uniqueId, 
            name: product.name, 
            price: product.price, 
            img: product.img, 
            quantity: 1,
            variant: product.variant || '' 
        });
    }
    saveCart();
    showToast('✓ ' + product.name + ' добавлен в корзину');
}

function renderCartModal() {
    var container = document.getElementById('cart-items');
    if (!container) return;

    if (cart.length === 0) {
        container.innerHTML = '<p style="text-align:center; padding:20px; color:#aaa;">Корзина пуста 🕯️</p>';
        var totalEl = document.getElementById('cart-total');
        if (totalEl) totalEl.textContent = 'Итого: 0 ₽';
        return;
    }

    var html = '';
    var total = 0;
    for (var i = 0; i < cart.length; i++) {
        var item = cart[i];
        total += item.price * item.quantity;
        html += '<div class="cart-item">' +
            '<img src="' + item.img + '" class="cart-item-img" onerror="this.src=\'https://i.ibb.co/7tvN62BM/1.jpg\'">' +
            '<div style="flex:1">' +
                '<strong>' + item.name + '</strong>' +
                (item.variant ? '<br><small style="color:#888">' + item.variant + '</small>' : '') +
                '<br>' + item.price + ' ₽ x ' + item.quantity +
            '</div>' +
            '<div class="cart-item-controls">' +
                '<button class="decr-item" data-idx="' + i + '">−</button>' +
                '<button class="incr-item" data-idx="' + i + '">+</button>' +
                '<button class="remove-item" data-idx="' + i + '">🗑</button>' +
            '</div>' +
        '</div>';
    }
    container.innerHTML = html;
    var totalEl = document.getElementById('cart-total');
    if (totalEl) totalEl.textContent = 'Итого: ' + total + ' ₽';

    document.querySelectorAll('.incr-item').forEach(function(btn) {
        btn.addEventListener('click', function(e) {
            var idx = parseInt(this.getAttribute('data-idx'));
            cart[idx].quantity++;
            saveCart();
        });
    });
    document.querySelectorAll('.decr-item').forEach(function(btn) {
        btn.addEventListener('click', function(e) {
            var idx = parseInt(this.getAttribute('data-idx'));
            if (cart[idx].quantity > 1) {
                cart[idx].quantity--;
            } else {
                cart.splice(idx, 1);
            }
            saveCart();
        });
    });
    document.querySelectorAll('.remove-item').forEach(function(btn) {
        btn.addEventListener('click', function(e) {
            var idx = parseInt(this.getAttribute('data-idx'));
            cart.splice(idx, 1);
            saveCart();
        });
    });
}

// ---------- ПОЛЬЗОВАТЕЛЬ (ПЛАШКА) ----------
function loadUserBadge() {
    fetch('api_user_me.php', { credentials: 'same-origin' })
        .then(function(r) { return r.json(); })
        .then(function(d) {
            var headerRight = document.querySelector('.header-right') || document.querySelector('.header-actions');
            
            if (headerRight && d && d.authenticated && d.user && d.user.name) {
                if (!document.getElementById('userPillContainer')) {
                    var container = document.createElement('div');
                    container.id = 'userPillContainer';
                    container.style.cssText = 'display:flex; align-items:center; gap:8px; margin-left:15px; white-space:nowrap;';
                    
                    var pill = document.createElement('a');
                    pill.href = 'account.html';
                    pill.className = 'sc-user-pill';
                    pill.innerHTML = '<i class="fas fa-user-circle"></i> <span>' + d.user.name.split(' ')[0] + '</span>';
                    
                    container.appendChild(pill);
                    headerRight.appendChild(container);
                    
                    var accountIcon = document.getElementById('accountIcon');
                    if (accountIcon) accountIcon.style.display = 'none';
                }
            }
        })
        .catch(function(err) { console.log('Auth check failed', err); });
}

// ---------- ПОИСК ----------
function initSearch() {
    var searchIcon = document.getElementById('search-icon');
    if (!searchIcon) return;
    
    var newIcon = searchIcon.cloneNode(true);
    searchIcon.parentNode.replaceChild(newIcon, searchIcon);
    
    newIcon.addEventListener('click', function(e) {
        e.preventDefault();
        var query = prompt('🔍 Поиск ароматов, свечей, диффузоров...', '');
        if (query && query.trim() !== '') {
            window.location.href = 'search.php?q=' + encodeURIComponent(query.trim());
        }
    });
}

// ---------- ТОСТ УВЕДОМЛЕНИЯ ----------
function showToast(message) {
    var toast = document.createElement('div');
    toast.className = 'toast-notification';
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(function() { toast.remove(); }, 3000);
}

// ---------- ЗАГРУЗКА ЦЕН ----------
function loadPrices() {
    return fetch('api_get_price.php', { cache: 'no-store' })
        .then(function(r) { return r.json(); })
        .then(function(data) {
            if (data.success && data.products) {
                for (var i = 0; i < data.products.length; i++) {
                    var p = data.products[i];
                    priceMap[p.product_id] = p.price;
                }
            }
            return priceMap;
        })
        .catch(function() { return {}; });
}

function getPrice(productId) {
    return priceMap[productId] || null;
}

// ---------- ОТПРАВКА ЗАКАЗА ----------
function submitOrder(name, phone) {
    if (cart.length === 0) {
        showToast('Корзина пуста');
        return;
    }
    
    var total = 0;
    for (var i = 0; i < cart.length; i++) {
        total += cart[i].price * cart[i].quantity;
    }
    
    var orderData = {
        items: cart.map(function(item) {
            return {
                name: item.name,
                price: item.price,
                quantity: item.quantity,
                total: item.price * item.quantity
            };
        }),
        total: total,
        customer_name: name,
        customer_phone: phone,
        date: new Date().toLocaleString('ru-RU')
    };
    
    return fetch('send_order.php', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(orderData)
    })
    .then(function(response) { return response.json(); })
    .then(function(result) {
        if (result.success) {
            cart = [];
            saveCart();
            showToast('✅ Заказ оформлен!');
            return result;
        } else {
            showToast('❌ Ошибка: ' + (result.error || 'не удалось отправить заказ'));
            return result;
        }
    })
    .catch(function() {
        showToast('❌ Ошибка соединения. Позвоните нам: +7 (927) 103-60-44');
        return { success: false, error: 'Network error' };
    });
}

// ---------- ИНИЦИАЛИЗАЦИЯ ----------
function init() {
    loadPrices();
    loadUserBadge();
    updateCartCount();
    initSearch();

    // Корзина
    var cartBtn = document.getElementById('cartBtn') || document.getElementById('cart-icon');
    var cartModal = document.getElementById('cart-modal');
    var cartClose = document.getElementById('close-cart');
    
    if (cartBtn && cartModal) {
        cartBtn.addEventListener('click', function() {
            renderCartModal();
            cartModal.classList.add('active');
            document.body.style.overflow = 'hidden';
        });
    }
    if (cartClose && cartModal) {
        cartClose.addEventListener('click', function() {
            cartModal.classList.remove('active');
            document.body.style.overflow = '';
        });
    }
    if (cartModal) {
        cartModal.addEventListener('click', function(e) {
            if (e.target === cartModal) {
                cartModal.classList.remove('active');
                document.body.style.overflow = '';
            }
        });
    }

    // Мобильное меню
    var burger = document.getElementById('burger') || document.getElementById('mobile-menu-toggle');
    var mainNav = document.getElementById('mainNav') || document.getElementById('mobile-nav');
    if (burger && mainNav) {
        burger.addEventListener('click', function() {
            mainNav.classList.toggle('open');
            mainNav.classList.toggle('active');
        });
        var navLinks = document.querySelectorAll('#mainNav a, #mobile-nav a, .mobile-nav-link');
        for (var i = 0; i < navLinks.length; i++) {
            navLinks[i].addEventListener('click', function() {
                mainNav.classList.remove('open');
                mainNav.classList.remove('active');
            });
        }
    }

    // Оформление заказа
    var checkoutForm = document.getElementById('checkoutForm') || document.getElementById('checkout-form');
    var checkoutBtn = document.getElementById('checkoutBtn') || document.getElementById('checkout-btn');
    if (checkoutBtn && checkoutForm) {
        checkoutBtn.addEventListener('click', function() {
            if (cart.length === 0) {
                showToast('Корзина пуста');
                return;
            }
            checkoutForm.classList.toggle('open');
            checkoutForm.classList.toggle('active');
        });
    }
    if (checkoutForm) {
        checkoutForm.addEventListener('submit', function(e) {
            e.preventDefault();
            var nameInput = document.getElementById('coName') || document.getElementById('checkout-name');
            var phoneInput = document.getElementById('coPhone') || document.getElementById('checkout-phone');
            var name = nameInput ? nameInput.value.trim() : '';
            var phone = phoneInput ? phoneInput.value.trim() : '';

            if (!name || !phone) {
                showToast('Заполните имя и телефон');
                return;
            }

            var btn = checkoutForm.querySelector('button[type="submit"]');
            btn.disabled = true;
            btn.textContent = 'Отправка...';

            submitOrder(name, phone)
                .then(function(result) {
                    if (result.success) {
                        if (nameInput) nameInput.value = '';
                        if (phoneInput) phoneInput.value = '';
                        checkoutForm.classList.remove('open');
                        checkoutForm.classList.remove('active');
                        
                        var modal = document.getElementById('cart-modal');
                        if (modal) modal.classList.remove('active');
                        document.body.style.overflow = '';
                    }
                })
                .finally(function() {
                    btn.disabled = false;
                    btn.textContent = 'Отправить';
                });
        });
    }
    console.log('✅ Common module initialized');
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}

window.SoulCandles = {
    cart: getCart,
    addToCart: addToCart,
    saveCart: saveCart,
    getPrice: getPrice,
    loadPrices: loadPrices,
    showToast: showToast,
    submitOrder: submitOrder
};
})();