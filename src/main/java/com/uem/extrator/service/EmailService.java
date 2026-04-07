package com.uem.extrator.service;

import com.uem.extrator.util.ConfigManager;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ObjectInputFilter;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailService {

    // logger instancia
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private static EmailService instance;

    private EmailService() {}

    public static synchronized EmailService getInstance() {
        if (instance == null) {
            instance = new EmailService();
        }
        return instance;
    }

    /**
     * Metodo genérico de envio.
     * @param assunto Título do E-mail
     * @param corpo Mensagem (pode ser HTML)
     */
    public void enviarAlerta(String assunto, String corpo) {
        ConfigManager config = ConfigManager.getInstance();
        String host = config.getSmtpHost();
        String port = config.getSmtpPort();
        String remetente = config.getSystemEmail(); // o robo

        // define quem recebe. se não tiver nenhum admin cadastrado manda para o proprio robo (loopback)
        String destinatario = config.getAdminEmail();
        if (destinatario == null || destinatario.trim().isEmpty()) {
            destinatario = remetente;
        }

        if (remetente == null || remetente.isEmpty() || host == null || host.isEmpty()) {
            logger.warn("[EmailService] SMTP não configurado. Ignorando envio.");
            return;
        }

        // configurações
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.ssl.trust", host);

        final String password = "wlveoqqztznlmupc";

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(remetente, password);
            }
        });

        try {
            // montagem da mensagem
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(remetente)); // de: Robô

            // Para: Admin (ou próprio robô se estiver vazio)
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));

            // E-mail
            message.setSubject("[Extrator Lattes] " + assunto);
            message.setContent(corpo, "text/html; charset=utf-8");

            // envio
            Transport.send(message);
            logger.info("[EmailService] E-mail enviado DE [{}] PARA [{}]: {}", remetente, destinatario, assunto);
        } catch (MessagingException e) {
            logger.error("[EmailService] Falha ao enviar E-mail: ", e);
        }
    }

    // utilitario para formatar lista
    public String formatarLista(java.util.List<String> itens) {
        StringBuilder sb = new StringBuilder("<ul>");
        for (String s : itens) {
            sb.append("<li>").append(s).append("</li>");
        }
        sb.append("</ul>");
        return sb.toString();
    }
}


