#!/usr/bin/env python3
"""Compra paga cuja resposta se perdeu: tela real do Terminal contra o Spring real.

Um proxy fica entre Terminal e backend: em modo de perda, entrega o request ao backend e
descarta a resposta, como uma rede que cai depois do envio. O cliente paga na maquininha,
o webhook assinado confirma a Order e a rede volta; a tela precisa chegar sozinha à
confirmação, sem segunda cobrança.
"""
import hashlib
import hmac
import json
import os
import subprocess
import sys
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import requests

BACKEND = os.environ["E2E_BACKEND_URL"].rstrip("/")
POINT = os.environ["POINT_STUB_URL"].rstrip("/")
PROXY_PORT = int(os.environ["E2E_PROXY_PORT"])
sys.path.insert(0, str(Path(os.environ["TERMINAL_PYTHON_ROOT"]).resolve() / "src"))

from PyQt5.QtWidgets import QApplication, QWidget

from app247_terminal.models.purchase_session import CompraSession
from app247_terminal.repositories.active_payment_repository import ActivePaymentStore
from app247_terminal.ui.screens.payment import PagamentoScreen

TERMINAL_ID = "30000000-0000-0000-0000-000000000001"
PRODUCT_ID = "40000000-0000-0000-0000-000000000001"
ESTOQUE_ID = "50000000-0000-0000-0000-000000000001"
CART_PAYLOAD = {"terminalId": TERMINAL_ID, "items": [{
    "productId": PRODUCT_ID, "quantity": 1, "expectedUnitPrice": 12.34, "codigoBarras": "7890000000001"}]}
LOST_PATHS = ("/pagamento/terminal/", "/order/")
RESPONSE_HOLD_SECONDS = float(os.environ["APP247_PAYMENT_READ_TIMEOUT_SECONDS"]) + 1
RECOVERY_TIMEOUT_SECONDS = 120


class Network:
    losing_responses = False
    lost = []


class Proxy(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def relay(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        headers = {k: v for k, v in self.headers.items() if k.lower() not in {"host", "content-length"}}
        response = requests.request(self.command, BACKEND + self.path, data=body, headers=headers, timeout=30)
        if Network.losing_responses and self.path.startswith(LOST_PATHS):
            Network.lost.append(f"{self.command} {self.path.split('?')[0]} -> {response.status_code}")
            time.sleep(RESPONSE_HOLD_SECONDS)
            self.close_connection = True
            return
        self.send_response(response.status_code)
        self.send_header("Content-Type", response.headers.get("Content-Type", "application/json"))
        self.send_header("Content-Length", str(len(response.content)))
        self.end_headers()
        self.wfile.write(response.content)

    do_GET = do_POST = relay


class Confirmation(QWidget):
    def __init__(self):
        super().__init__()
        self.shown = False

    def mostrar_tela(self, recovered_amount=None):
        self.shown = True


class TerminalApp(QWidget):
    """Só a casca da janela principal; sessão, tela, workers e checkpoint são reais."""

    def __init__(self, store):
        super().__init__()
        self.compra_session = CompraSession(
            self, payment_store=store, terminal_id_provider=lambda: TERMINAL_ID)
        self.confirmacao = Confirmation()
        self.terminal = QWidget()
        self.welcome = QWidget()
        self.stacked_widget = self
        self.current = None
        self.reset_outcomes = []

    def current_terminal_id(self):
        return TERMINAL_ID

    def setCurrentWidget(self, widget):
        self.current = widget

    def reset_compra(self, outcome="cancelled"):
        self.reset_outcomes.append(outcome)
        self.compra_session.reset()


def sql(query):
    command = ["docker", "compose", "-f", os.environ["E2E_COMPOSE_FILE"], "exec", "-T", "mysql-e2e", "mysql",
               "-N", "-B", "-uapp247_e2e", f"-p{os.environ['E2E_DB_PASSWORD']}", "app247_e2e"]
    return subprocess.run(command, input=query, text=True, capture_output=True, check=True).stdout.strip()


def check(name, condition, detail=""):
    if not condition:
        raise AssertionError(f"{name}: {detail or 'condição não atendida'}")
    print(f"[E2E] {name}: OK{' — ' + str(detail) if detail else ''}", flush=True)


def wait_until(app, condition, timeout, description):
    deadline = time.time() + timeout
    while time.time() < deadline:
        app.processEvents()
        if condition():
            return
        time.sleep(0.05)
    raise AssertionError(f"tempo esgotado aguardando {description}")


def order_row(cart_id):
    row = sql(f"SELECT o.id, o.status, p.provider_order_id, p.status FROM orders o "
              f"JOIN payment_attempt p ON p.order_id=o.id WHERE o.carrinho_id='{cart_id}';")
    return row.split("\t") if row else None


def send_paid_webhook(provider_order_id):
    remote = requests.get(f"{POINT}/v1/orders/{provider_order_id}", timeout=5).json()
    request_id = str(uuid.uuid4())
    timestamp = str(int(time.time() * 1000))
    manifest = f"id:{provider_order_id.lower()};request-id:{request_id};ts:{timestamp};"
    signature = hmac.new(os.environ["E2E_MP_WEBHOOK_SECRET"].encode(), manifest.encode(), hashlib.sha256).hexdigest()
    response = requests.post(
        f"{BACKEND}/webhook/mercadopago", params={"data.id": provider_order_id},
        headers={"x-signature": f"ts={timestamp},v1={signature}", "x-request-id": request_id},
        json={"action": "order.processed", "type": "order", "user_id": remote["user_id"], "live_mode": False,
              "data": {"id": provider_order_id, "version": remote["version"],
                       "external_reference": remote["external_reference"]}}, timeout=10)
    response.raise_for_status()


def customer_pays_while_network_is_down(app, shell, screen):
    requests.post(f"{POINT}/__control/reset", timeout=5).raise_for_status()
    Network.losing_responses, Network.lost = True, []
    session = shell.compra_session
    check("pagamento iniciado na tela", screen.iniciar_pagamento(CART_PAYLOAD, "R$ 12,34") is not False)
    wait_until(app, lambda: session.cart_id and order_row(session.cart_id)
               and order_row(session.cart_id)[2] != "NULL", 30, "cobrança na maquininha")
    cart_id = session.cart_id
    wait_until(app, lambda: Network.lost, 10, "resposta perdida")
    check("Terminal sem orderId", session.order_id is None, Network.lost[0])

    order_id, _, provider_order_id, _ = order_row(cart_id)
    requests.post(f"{POINT}/__control/state", json={"state": "APPROVED"}, timeout=5).raise_for_status()
    send_paid_webhook(provider_order_id)
    wait_until(app, lambda: order_row(cart_id)[1] == "PROCESSED", 30, "Order paga no backend")
    check("backend registrou o pagamento", not shell.confirmacao.shown, f"orderId={order_id}")
    return cart_id, order_id


def assert_recovered(app, shell, cart_id, order_id, stock_before):
    wait_until(app, lambda: shell.confirmacao.shown, RECOVERY_TIMEOUT_SECONDS, "tela de confirmação")
    session = shell.compra_session
    attempts = sql(f"SELECT COUNT(*) FROM payment_attempt WHERE order_id='{order_id}';")
    remote = requests.get(f"{POINT}/__control", timeout=5).json()["createCalls"]
    stock_after = sql(f"SELECT quantidade FROM estoque_condominio WHERE id='{ESTOQUE_ID}';")
    check("Terminal mostra a confirmação", shell.current is shell.confirmacao)
    check("Terminal recuperou a Order paga", session.order_id == order_id and session.cart_id == cart_id, order_id)
    check("sem segunda tentativa", attempts == "1", f"attempts={attempts}")
    check("sem segunda cobrança", remote == 1, f"operações remotas={remote}")
    check("estoque baixou uma vez", float(stock_before) - float(stock_after) == 1, f"{stock_before} -> {stock_after}")
    check("checkpoint local limpo", session.payment_store.load(TERMINAL_ID) is None)
    check("sem reset indevido", shell.reset_outcomes == [])


def same_session(app, store):
    print("[E2E] cenário: rede volta com a tela aberta", flush=True)
    stock = sql(f"SELECT quantidade FROM estoque_condominio WHERE id='{ESTOQUE_ID}';")
    shell = TerminalApp(store)
    screen = PagamentoScreen(shell)
    cart_id, order_id = customer_pays_while_network_is_down(app, shell, screen)
    Network.losing_responses = False
    assert_recovered(app, shell, cart_id, order_id, stock)
    screen.parar_workers()
    shell.compra_session.reset()


def after_restart(app, store):
    print("[E2E] cenário: Terminal reinicia durante a queda", flush=True)
    stock = sql(f"SELECT quantidade FROM estoque_condominio WHERE id='{ESTOQUE_ID}';")
    before = TerminalApp(store)
    first_screen = PagamentoScreen(before)
    cart_id, order_id = customer_pays_while_network_is_down(app, before, first_screen)
    first_screen.parar_workers()

    shell = TerminalApp(store)
    screen = PagamentoScreen(shell)
    check("checkpoint restaurado", shell.compra_session.restore_pending_payment(TERMINAL_ID))
    check("carrinho no checkpoint", shell.compra_session.cart_id == cart_id, cart_id)
    Network.losing_responses = False
    screen.recuperar_apos_startup()
    assert_recovered(app, shell, cart_id, order_id, stock)
    screen.parar_workers()


def main():
    app = QApplication.instance() or QApplication([])
    server = ThreadingHTTPServer(("127.0.0.1", PROXY_PORT), Proxy)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    store = ActivePaymentStore(Path(os.environ["E2E_RUNTIME_DIR"]) / "terminal.db")
    same_session(app, store)
    after_restart(app, store)
    server.shutdown()
    print(json.dumps({"resultado": "aprovado"}), flush=True)


if __name__ == "__main__":
    main()
