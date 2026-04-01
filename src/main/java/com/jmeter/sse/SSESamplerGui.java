package com.jmeter.sse;

import org.apache.jmeter.samplers.gui.AbstractSamplerGui;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * JMeter GUI panel for the SSE Sampler.
 */
public class SSESamplerGui extends AbstractSamplerGui {

    private JTextField urlField;
    private JTextField durationField;
    private JTextArea  headersArea;

    public SSESamplerGui() {
        init();
    }

    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());
        add(makeTitlePanel(), BorderLayout.NORTH);
        add(buildMainPanel(), BorderLayout.CENTER);
    }

    private JPanel buildMainPanel() {
        JPanel container = new JPanel(new BorderLayout(0, 8));

        // --- Connection settings ---
        JPanel connectionPanel = new JPanel(new GridBagLayout());
        connectionPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Connection Settings",
                TitledBorder.LEFT,
                TitledBorder.TOP));

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.anchor = GridBagConstraints.WEST;
        labelGbc.insets = new Insets(4, 6, 4, 6);
        labelGbc.gridx  = 0;
        labelGbc.gridy  = 0;
        labelGbc.fill   = GridBagConstraints.NONE;

        GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.anchor    = GridBagConstraints.WEST;
        fieldGbc.insets    = new Insets(4, 0, 4, 6);
        fieldGbc.gridx     = 1;
        fieldGbc.gridy     = 0;
        fieldGbc.fill      = GridBagConstraints.HORIZONTAL;
        fieldGbc.weightx   = 1.0;

        // URL row
        connectionPanel.add(new JLabel("SSE Endpoint URL:"), labelGbc);
        urlField = new JTextField(40);
        urlField.setToolTipText("Full SSE URL — supports JMeter ${variables}");
        connectionPanel.add(urlField, fieldGbc);

        // Duration row
        labelGbc.gridy = 1;
        fieldGbc.gridy = 1;
        connectionPanel.add(new JLabel("Listen Duration (seconds):"), labelGbc);
        durationField = new JTextField("10", 10);
        durationField.setToolTipText("How many seconds to listen for SSE events before stopping");
        JPanel durationWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        durationWrapper.add(durationField);
        connectionPanel.add(durationWrapper, fieldGbc);

        // --- Custom headers ---
        JPanel headersPanel = new JPanel(new BorderLayout(4, 4));
        headersPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Custom Request Headers  (one per line: Name: Value)",
                TitledBorder.LEFT,
                TitledBorder.TOP));

        headersArea = new JTextArea(5, 40);
        headersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        headersArea.setToolTipText("Example:\nAuthorization: Bearer mytoken\nX-Custom-Header: value");
        headersPanel.add(new JScrollPane(headersArea), BorderLayout.CENTER);

        container.add(connectionPanel, BorderLayout.NORTH);
        container.add(headersPanel,    BorderLayout.CENTER);
        return container;
    }

    // Fix #5: Return a non-null key to prevent NPE in some JMeter versions
    @Override
    public String getLabelResource() {
        return "sse_sampler_title";
    }

    @Override
    public String getStaticLabel() {
        return "SSE Sampler";
    }

    @Override
    public TestElement createTestElement() {
        SSESampler sampler = new SSESampler();
        modifyTestElement(sampler);
        return sampler;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        element.clear();
        configureTestElement(element);
        element.setProperty(SSESampler.URL,      urlField.getText());
        element.setProperty(SSESampler.DURATION, durationField.getText());
        element.setProperty(SSESampler.HEADERS,  headersArea.getText());
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
        urlField.setText(element.getPropertyAsString(SSESampler.URL));
        durationField.setText(element.getPropertyAsString(SSESampler.DURATION, "10"));
        headersArea.setText(element.getPropertyAsString(SSESampler.HEADERS, ""));
    }

    @Override
    public void clearGui() {
        super.clearGui();
        urlField.setText("");
        durationField.setText("10");
        headersArea.setText("");
    }
}