/*
 * Copyright (C) 2015
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.cleverbus.admin.web.msg;

import static org.springframework.util.StringUtils.hasText;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.cleverbus.api.entity.Message;
import org.cleverbus.common.log.Log;
import org.cleverbus.modules.ExternalSystemEnum;
import org.cleverbus.spi.msg.MessageService;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;


/**
 * Controller that encapsulates actions around messages.
 *
 * @author <a href="mailto:petr.juza@cleverlance.com">Petr Juza</a>
 * @author <a href="mailto:tomas.hanus@cleverlance.com">Tomas Hanus</a>
 */
@Controller
@RequestMapping("/messages")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageLogParser messageLogParser;

    @RequestMapping(value = "/{msgId}", method = RequestMethod.GET)
    public String getMsgDetailByMsgId(@PathVariable("msgId") Long msgId, Model model)  {
        Message msg = messageService.findEagerMessageById(msgId);

        model.addAttribute("requestMsgId", msgId);

        if (msg != null) {
            // pretty-print envelope
            if (StringUtils.isNotEmpty(msg.getEnvelope())) {
                msg.setEnvelope(prettyPrintXML(msg.getEnvelope()));
            }
            // pretty-print payload
            if (StringUtils.isNotEmpty(msg.getPayload())) {
                msg.setPayload(prettyPrintXML(msg.getPayload()));
            }

            model.addAttribute("msg", msg);
        }

        return "msg";
    }

    @RequestMapping(value = "/{msgId}/log", method = RequestMethod.GET)
    public String getLogOfMsgByMsgId(@PathVariable("msgId") Long msgId, Model model)  {
        Message msg = messageService.findMessageById(msgId);

        model.addAttribute("requestMsgId", msgId);

        if (msg != null) {
            String correlationId = msg.getCorrelationId();

            model.addAttribute("correlationId", correlationId);

            List<String> logLines = new ArrayList<String>();
            try {
                long start = System.currentTimeMillis();
                SortedSet<LocalDate> logDates = getMsgDates(msg);
                logDates.add(new LocalDate()); // adding today just in case

                Log.debug("Starts searching log for correlationId = " + correlationId);

                for (LocalDate logDate : logDates) {
                    logLines.addAll(messageLogParser.getLogLines(correlationId, logDate.toDate()));
                }

                Log.debug("Finished searching log in " + (System.currentTimeMillis() - start) + " ms.");

                model.addAttribute("log", StringUtils.join(logLines, "\n"));
            } catch (IOException ex) {
                model.addAttribute("logErr", "Error occurred during reading log file: " + ex.getMessage());
            }
        }

        return "msgLog";
    }

    /**
     * Creates a set of message dates, to search logs for these dates.
     * This set contains just the dates of the following timestamps:
     * <ul>
     * <li>source system timestamp</li>
     * <li>received timestamp</li>
     * <li>start processing timestamp</li>
     * <li>last update timestamp</li>
     * </ul>
     * It's a Set, so that only distinct dates will be included.
     * It's a SortedSet, so that the dates are in their natural order.
     * The elements are LocalDate (without time),
     * so that the dates will be in the correct order
     * and the set will correctly not include duplicates.
     *
     * @param msg the message to find dates for
     * @return a set of dates
     */
    private SortedSet<LocalDate> getMsgDates(Message msg) {
        TreeSet<LocalDate> logDates = new TreeSet<LocalDate>();
        Date[] msgDates = new Date[]{
                msg.getMsgTimestamp(),
                msg.getReceiveTimestamp(),
                msg.getStartProcessTimestamp(),
                msg.getLastUpdateTimestamp()
        };
        for (Date msgDate : msgDates) {
            if (msgDate != null) {
                logDates.add(new LocalDate(msgDate));
            }
        }
        return logDates;
    }

    @RequestMapping(value = "/searchForm", method = RequestMethod.GET)
    public String getSearchForm(@RequestParam(value = "source", required = false) ExternalSystemEnum sourceSystem,
            @RequestParam(value = "correlation", required = false) String correlationId,
            @ModelAttribute("model") ModelMap model)  {

        addExternalSystemsIntoModel(model);

        if (hasText(correlationId)) {
            Message msg = messageService.findMessageByCorrelationId(correlationId,
                    sourceSystem == ExternalSystemEnum.UNDEFINED ? null : sourceSystem);
            if (msg != null) {
                return "redirect:/web/admin/messages/" + msg.getMsgId();
            } else {
                model.addAttribute("resultFailed", Boolean.TRUE);

                return "msg_search";
            }
        }

        model.put("source", sourceSystem);
        model.put("correlation", correlationId);

        return "msg_search";
    }

    private void addExternalSystemsIntoModel(ModelMap model) {
        Map<String, String> systemsMap = new LinkedHashMap<String, String>();
        for (ExternalSystemEnum systemEnum : ExternalSystemEnum.values()) {
            systemsMap.put(systemEnum.name(), systemEnum.name());
        }

        model.addAttribute("systemsMap", systemsMap);
    }

    @RequestMapping(value = "/searchForm", method = RequestMethod.POST)
    public String searchMessage(@RequestParam("sourceSystem") ExternalSystemEnum sourceSystem,
            @RequestParam("correlationId") String correlationId,
            @ModelAttribute("model") ModelMap model)  {

        Message msg = messageService.findMessageByCorrelationId(correlationId,
                sourceSystem == ExternalSystemEnum.UNDEFINED ? null : sourceSystem);

        if (msg != null) {
            return "redirect:/web/admin/messages/" + msg.getMsgId();
        } else {
            addExternalSystemsIntoModel(model);
            model.addAttribute("resultFailed", Boolean.TRUE);

            return "msg_search";
        }
    }

    @RequestMapping(value = "/messagesByContent", method = { RequestMethod.GET, RequestMethod.POST })
    public String showMessagesByContent(@RequestParam(value = "substring", required = false) String substring,
            @ModelAttribute("model") ModelMap model) {

        if (StringUtils.isNotBlank(substring)) {
            List<Message> messageList = messageService.findMessagesByContent(substring);

            if (!messageList.isEmpty()) {
                model.addAttribute("messageList", messageList);
            }
            model.put("substring", substring);
        }

        return "msgByContent";
    }

    /**
     * Converts input XML to "nice" XML.
     *
     * @param original the original XML
     * @return pretty XML
     */
    static String prettyPrintXML(String original) {
        try {
            OutputFormat format = OutputFormat.createPrettyPrint();
            Document document = DocumentHelper.parseText(original);
            StringWriter sw = new StringWriter();
            XMLWriter writer = new XMLWriter(sw, format);
            writer.write(document);
            return sw.toString();
        } catch (Exception exc) {
            Log.debug("Error pretty-printing XML: ", exc);
            return original;
        }
    }
}
