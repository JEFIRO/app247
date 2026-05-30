package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.PasswordRecovery;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service

@RequiredArgsConstructor
public class EmailService {
    @Autowired
    UserService userService;

    private final JavaMailSender mailSender;

    public void enviarEmail(String destinatario, String assunto, String mensagem) {

        SimpleMailMessage email = new SimpleMailMessage();

        email.setTo(destinatario);
        email.setSubject(assunto);
        email.setText(mensagem);

        mailSender.send(email);
    }

    public void enviarEmail(PasswordRecovery passwordRecovery) throws Exception {

        MimeMessage message = mailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(passwordRecovery.email());
        helper.setSubject("Recuperação de senha - 24/7");

        helper.setText(gerarTemplate(passwordRecovery), true);

        mailSender.send(message);
    }

    public String gerarTemplate(PasswordRecovery recovery) {
        String nome = recovery.nome();
        String email = recovery.email();
        String code = recovery.code();

        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Recuperação de Senha</title>
                  <style>
                    @import url('https://fonts.googleapis.com/css2?family=DM+Serif+Display:ital@0;1&family=DM+Sans:wght@300;400;500;600&display=swap');
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { background-color: #0e0e10; font-family: 'DM Sans', sans-serif; padding: 40px 16px; }
                    .wrapper { max-width: 560px; margin: 0 auto; }
                    .header { text-align: center; padding: 40px 0 32px; }
                    .logo { display: inline-flex; align-items: center; gap: 10px; text-decoration: none; }
                    .logo-icon { width: 36px; height: 36px; background: linear-gradient(135deg, #c8a96e, #f0d898); border-radius: 10px; display: flex; align-items: center; justify-content: center; }
                    .logo-icon svg { width: 20px; height: 20px; }
                    .logo-name { font-family: 'DM Serif Display', serif; font-size: 22px; color: #f0d898; letter-spacing: 0.3px; }
                    .card { background: #18181c; border: 1px solid #2a2a30; border-radius: 20px; overflow: hidden; }
                    .card-banner { background: linear-gradient(135deg, #1c1a14 0%, #2a2416 50%, #1c1a14 100%); padding: 48px 40px 36px; text-align: center; position: relative; border-bottom: 1px solid #2a2416; }
                    .card-banner::before { content: ''; position: absolute; inset: 0; background: radial-gradient(ellipse at 50% 0%, rgba(200,169,110,0.12) 0%, transparent 70%); pointer-events: none; }
                    .icon-circle { width: 72px; height: 72px; background: linear-gradient(135deg, rgba(200,169,110,0.15), rgba(200,169,110,0.05)); border: 1px solid rgba(200,169,110,0.3); border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 20px; }
                    .icon-circle svg { width: 32px; height: 32px; color: #c8a96e; }
                    .card-banner h1 { font-family: 'DM Serif Display', serif; font-size: 26px; color: #f5ede0; margin-bottom: 10px; line-height: 1.2; }
                    .card-banner p { font-size: 14px; color: #7a7068; line-height: 1.6; }
                    .card-body { padding: 36px 40px; }
                    .greeting { font-size: 15px; color: #b0a898; margin-bottom: 16px; line-height: 1.6; }
                    .greeting strong { color: #e8ddd0; font-weight: 500; }
                    .message { font-size: 14px; color: #7a7068; line-height: 1.7; margin-bottom: 32px; }
                    .code-block { background: #111114; border: 1px solid #2a2a30; border-radius: 14px; padding: 20px 24px; text-align: center; margin-bottom: 28px; }
                    .code-label { font-size: 11px; color: #4a4a52; text-transform: uppercase; letter-spacing: 1.5px; margin-bottom: 12px; }
                    .code-value { font-family: 'Courier New', monospace; font-size: 36px; font-weight: 700; letter-spacing: 10px; color: #c8a96e; margin-bottom: 10px; }
                    .code-hint { font-size: 11.5px; color: #3a3a42; }
                    .expiry { display: flex; align-items: center; gap: 10px; background: #111114; border: 1px solid #22222a; border-radius: 10px; padding: 12px 16px; margin-bottom: 32px; }
                    .expiry svg { flex-shrink: 0; color: #c8a96e; width: 16px; height: 16px; }
                    .expiry p { font-size: 12.5px; color: #5a5a62; }
                    .expiry strong { color: #c8a96e; }
                    .warning { background: rgba(200,169,110,0.05); border-left: 3px solid rgba(200,169,110,0.4); border-radius: 0 8px 8px 0; padding: 14px 16px; margin-bottom: 32px; }
                    .warning p { font-size: 12.5px; color: #7a7068; line-height: 1.6; }
                    .warning strong { color: #c8a96e; }
                    .card-footer { border-top: 1px solid #22222a; padding: 24px 40px; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
                    .footer-text { font-size: 11.5px; color: #3a3a42; line-height: 1.5; }
                    .footer-text a { color: #5a5a62; text-decoration: none; }
                    .footer-links { display: flex; gap: 16px; flex-shrink: 0; }
                    .footer-links a { font-size: 11px; color: #3a3a42; text-decoration: none; }
                    .outer-footer { text-align: center; padding: 24px 0 0; }
                    .outer-footer p { font-size: 11px; color: #2e2e36; line-height: 1.6; }
                  </style>
                </head>
                <body>
                  <div class="wrapper">
                    <div class="header">
                      <a href="#" class="logo">
                        <div class="logo-icon">
                          <svg viewBox="0 0 24 24" fill="none" stroke="#1a1410" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                            <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
                          </svg>
                        </div>
                        <span class="logo-name">Marca</span>
                      </a>
                    </div>
                    <div class="card">
                      <div class="card-banner">
                        <div class="icon-circle">
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                            <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                            <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                          </svg>
                        </div>
                        <h1>Redefinição de senha</h1>
                        <p>Recebemos uma solicitação para redefinir<br>a senha da sua conta.</p>
                      </div>
                      <div class="card-body">
                        <p class="greeting">Olá, <strong>{{NOME}}</strong> 👋</p>
                        <p class="message">
                          Identificamos uma solicitação de redefinição de senha associada a este endereço de e-mail.
                          Use o código abaixo para criar uma nova senha segura para sua conta.
                        </p>
                        <div class="code-block">
                          <p class="code-label">Seu código de recuperação</p>
                          <div class="code-value">{{CODE}}</div>
                          <p class="code-hint">Digite este código na tela de redefinição de senha.</p>
                        </div>
                        <div class="expiry">
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <circle cx="12" cy="12" r="10"/>
                            <polyline points="12 6 12 12 16 14"/>
                          </svg>
                          <p>Este código expira em <strong>15 minutos</strong> a partir do recebimento deste e-mail.</p>
                        </div>
                        <div class="warning">
                          <p>
                            <strong>Não solicitou isso?</strong> Você pode ignorar este e-mail com segurança.
                            Sua senha permanece a mesma e nenhuma alteração foi feita na sua conta.
                            Se você se preocupa com a segurança, entre em contato conosco.
                          </p>
                        </div>
                      </div>
                      <div class="card-footer">
                        <p class="footer-text">
                          Enviado para <a href="#">{{EMAIL}}</a><br>
                          © 2026 Marca. Todos os direitos reservados.
                        </p>
                        <div class="footer-links">
                          <a href="#">Privacidade</a>
                          <a href="#">Suporte</a>
                          <a href="#">Cancelar</a>
                        </div>
                      </div>
                    </div>
                    <div class="outer-footer">
                      <p>Você está recebendo este e-mail porque uma solicitação de<br>redefinição de senha foi feita para sua conta.</p>
                    </div>
                  </div>
                </body>
                </html>
                """.replace("{{NOME}}", nome).replace("{{CODE}}", code).replace("{{EMAIL}}", email);
    }


    public void enviarEmail(User user) throws Exception {

        MimeMessage message = mailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(user.getEmail());
        helper.setSubject("Boas Vindas - 24/7");

        helper.setText(gerarTemplateBoasVindas(user), true);

        mailSender.send(message);
    }

    public String gerarTemplateBoasVindas(User usuario) {
        String nome = usuario.getNome();
        String email = usuario.getEmail();
        String dataCadastro = usuario.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH'h'mm"));

        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Boas-vindas</title>
                  <style>
                    @import url('https://fonts.googleapis.com/css2?family=DM+Serif+Display:ital@0;1&family=DM+Sans:wght@300;400;500;600&display=swap');
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { background-color: #0e0e10; font-family: 'DM Sans', sans-serif; padding: 40px 16px; }
                    .wrapper { max-width: 560px; margin: 0 auto; }
                    .header { text-align: center; padding: 40px 0 32px; }
                    .logo { display: inline-flex; align-items: center; gap: 10px; text-decoration: none; }
                    .logo-icon { width: 36px; height: 36px; background: linear-gradient(135deg, #c8a96e, #f0d898); border-radius: 10px; display: flex; align-items: center; justify-content: center; }
                    .logo-icon svg { width: 20px; height: 20px; }
                    .logo-name { font-family: 'DM Serif Display', serif; font-size: 22px; color: #f0d898; letter-spacing: 0.3px; }
                    .card { background: #18181c; border: 1px solid #2a2a30; border-radius: 20px; overflow: hidden; }
                    .card-banner { background: linear-gradient(160deg, #1c1a14 0%, #2a2416 40%, #1a1c14 100%); padding: 52px 40px 40px; text-align: center; position: relative; border-bottom: 1px solid #2a2416; overflow: hidden; }
                    .card-banner::before { content: ''; position: absolute; inset: 0; background: radial-gradient(ellipse at 50% -10%, rgba(200,169,110,0.18) 0%, transparent 65%); pointer-events: none; }
                    .stars { position: absolute; top: 0; left: 0; right: 0; bottom: 0; pointer-events: none; overflow: hidden; }
                    .star { position: absolute; width: 3px; height: 3px; background: #c8a96e; border-radius: 50%; opacity: 0.25; }
                    .star:nth-child(1) { top: 18%; left: 12%; width: 2px; height: 2px; opacity: 0.2; }
                    .star:nth-child(2) { top: 30%; left: 88%; width: 3px; height: 3px; opacity: 0.3; }
                    .star:nth-child(3) { top: 60%; left: 7%;  width: 2px; height: 2px; opacity: 0.15; }
                    .star:nth-child(4) { top: 70%; left: 92%; width: 2px; height: 2px; opacity: 0.2; }
                    .star:nth-child(5) { top: 10%; left: 55%; width: 2px; height: 2px; opacity: 0.1; }
                    .star:nth-child(6) { top: 85%; left: 40%; width: 3px; height: 3px; opacity: 0.15; }
                    .welcome-icon { width: 80px; height: 80px; background: linear-gradient(135deg, rgba(200,169,110,0.18), rgba(200,169,110,0.06)); border: 1px solid rgba(200,169,110,0.28); border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 20px; position: relative; }
                    .welcome-icon::after { content: ''; position: absolute; inset: -4px; border-radius: 50%; border: 1px dashed rgba(200,169,110,0.15); }
                    .welcome-icon svg { width: 36px; height: 36px; color: #c8a96e; }
                    .card-banner h1 { font-family: 'DM Serif Display', serif; font-size: 28px; color: #f5ede0; margin-bottom: 10px; line-height: 1.25; }
                    .card-banner h1 em { font-style: italic; color: #c8a96e; }
                    .card-banner p { font-size: 14px; color: #7a7068; line-height: 1.6; }
                    .card-body { padding: 36px 40px; }
                    .greeting { font-size: 15px; color: #b0a898; margin-bottom: 14px; line-height: 1.6; }
                    .greeting strong { color: #e8ddd0; font-weight: 500; }
                    .message { font-size: 14px; color: #7a7068; line-height: 1.75; margin-bottom: 32px; }
                    .info-card { background: #111114; border: 1px solid #22222a; border-radius: 14px; overflow: hidden; margin-bottom: 32px; }
                    .info-card-header { padding: 14px 20px; border-bottom: 1px solid #1e1e24; display: flex; align-items: center; gap: 8px; }
                    .info-card-header svg { width: 14px; height: 14px; color: #c8a96e; flex-shrink: 0; }
                    .info-card-header span { font-size: 11px; color: #4a4a52; text-transform: uppercase; letter-spacing: 1.5px; }
                    .info-row { display: flex; align-items: center; justify-content: space-between; padding: 14px 20px; border-bottom: 1px solid #1a1a20; gap: 12px; }
                    .info-row:last-child { border-bottom: none; }
                    .info-label { font-size: 12px; color: #4a4a52; flex-shrink: 0; }
                    .info-value { font-size: 13px; color: #c8c0b4; text-align: right; word-break: break-all; }
                    .info-value.highlight { color: #c8a96e; font-weight: 500; }
                    .highlight-box { background: linear-gradient(135deg, rgba(200,169,110,0.07), rgba(200,169,110,0.03)); border: 1px solid rgba(200,169,110,0.2); border-radius: 14px; padding: 20px 22px; margin-bottom: 32px; display: flex; gap: 14px; align-items: flex-start; }
                    .highlight-box svg { width: 20px; height: 20px; color: #c8a96e; flex-shrink: 0; margin-top: 1px; }
                    .highlight-box p { font-size: 13.5px; color: #8a8278; line-height: 1.65; }
                    .highlight-box strong { color: #c8a96e; }
                    .warning { background: rgba(200,169,110,0.05); border-left: 3px solid rgba(200,169,110,0.35); border-radius: 0 8px 8px 0; padding: 14px 16px; margin-bottom: 32px; }
                    .warning p { font-size: 12.5px; color: #7a7068; line-height: 1.6; }
                    .warning strong { color: #c8a96e; }
                    .card-footer { border-top: 1px solid #22222a; padding: 24px 40px; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
                    .footer-text { font-size: 11.5px; color: #3a3a42; line-height: 1.5; }
                    .footer-text a { color: #5a5a62; text-decoration: none; }
                    .footer-links { display: flex; gap: 16px; flex-shrink: 0; }
                    .footer-links a { font-size: 11px; color: #3a3a42; text-decoration: none; }
                    .outer-footer { text-align: center; padding: 24px 0 0; }
                    .outer-footer p { font-size: 11px; color: #2e2e36; line-height: 1.6; }
                  </style>
                </head>
                <body>
                  <div class="wrapper">
                    <div class="header">
                      <a href="#" class="logo">
                        <div class="logo-icon">
                          <svg viewBox="0 0 24 24" fill="none" stroke="#1a1410" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                            <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
                          </svg>
                        </div>
                        <span class="logo-name">Marca</span>
                      </a>
                    </div>
                    <div class="card">
                      <div class="card-banner">
                        <div class="stars">
                          <div class="star"></div><div class="star"></div><div class="star"></div>
                          <div class="star"></div><div class="star"></div><div class="star"></div>
                        </div>
                        <div class="welcome-icon">
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                          </svg>
                        </div>
                        <h1>Bem-vindo à <em>Marca</em></h1>
                        <p>Sua conta foi criada com sucesso.<br>Estamos felizes em ter você por aqui.</p>
                      </div>
                      <div class="card-body">
                        <p class="greeting">Olá, <strong>{{NOME}}</strong> 👋</p>
                        <p class="message">
                          É um prazer ter você como parte da nossa comunidade. Sua conta está ativa e pronta para uso.
                          Abaixo estão os dados do seu cadastro para sua conferência.
                        </p>
                        <div class="info-card">
                          <div class="info-card-header">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                              <circle cx="12" cy="7" r="4"/>
                            </svg>
                            <span>Dados do cadastro</span>
                          </div>
                          <div class="info-row">
                            <span class="info-label">Nome</span>
                            <span class="info-value highlight">{{NOME}}</span>
                          </div>
                          <div class="info-row">
                            <span class="info-label">E-mail</span>
                            <span class="info-value">{{EMAIL}}</span>
                          </div>
                          <div class="info-row">
                            <span class="info-label">Data de cadastro</span>
                            <span class="info-value">{{DATA_CADASTRO}}</span>
                          </div>
                        </div>
                        <div class="highlight-box">
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                            <circle cx="12" cy="12" r="10"/>
                            <line x1="12" y1="8" x2="12" y2="12"/>
                            <line x1="12" y1="16" x2="12.01" y2="16"/>
                          </svg>
                          <p>
                            <strong>Guarde seus dados com segurança.</strong> Nunca compartilhe sua senha com terceiros.
                            Em caso de dúvidas ou problemas, nossa equipe de suporte está disponível para ajudá-lo.
                          </p>
                        </div>
                        <div class="warning">
                          <p>
                            <strong>Não criou esta conta?</strong> Se você não reconhece este cadastro, entre em contato
                            imediatamente com nosso suporte para que possamos proteger seus dados.
                          </p>
                        </div>
                      </div>
                      <div class="card-footer">
                        <p class="footer-text">
                          Enviado para <a href="#">{{EMAIL}}</a><br>
                          © 2026 Marca. Todos os direitos reservados.
                        </p>
                        <div class="footer-links">
                          <a href="#">Privacidade</a>
                          <a href="#">Suporte</a>
                          <a href="#">Cancelar</a>
                        </div>
                      </div>
                    </div>
                    <div class="outer-footer">
                      <p>Você está recebendo este e-mail porque realizou um cadastro<br>em nossa plataforma.</p>
                    </div>
                  </div>
                </body>
                </html>
                """.replace("{{NOME}}", nome).replace("{{EMAIL}}", email).replace("{{DATA_CADASTRO}}", dataCadastro);
    }
}
