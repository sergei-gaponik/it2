# Aufgabenstellung RTSP-Streaming
Die Aufgaben beziehen sich auf den Beleg Videostreaming für das Modul Internettechnologien 2.

## Aufgaben

### 1. Client
Ergänzen Sie die Klasse Client entsprechend der in der Projektbeschreibung und den Kommentaren im Quelltext gegebenen Hinweisen. Damit wird das RTSP-Protokoll im Client vervollständigt. Lassen die den Abschnitt zur Statistik zunächst unbearbeitet. Dieser Teil in Abschnitt 5 bearbeitet.

### 2. Server
Ergänzen Sie die Klasse RTPpacket entsprechend der in der Projektbeschreibung und den Kommentaren im Quelltext gegebenen Hinweisen.

### 3. RTSP-Methoden
Ergänzen Sie die RTSP-Methoden OPTIONS und DESCRIBE anhand der Beispiele aus [RFC 2326](https://www.ietf.org/rfc/rfc2326.txt) und [RFC 2327](https://www.ietf.org/rfc/rfc2327.txt). 
Die Serverantwort muss nicht ausgewertet werden. Die Anzeige der Antwort in der Konsole des Clients genügt.

Es ist ausreichend, sich bei der DESCRIBE-Methode auf das Beispielvideo zu beziehen und die Antwort auf dem Server statisch zu hinterlegen. 

### 4. Simulation von Paketverlusten
Simulieren Sie Paketverluste und eine variable Verzögerung im Netz, indem Sie am Sender eine wahlweise Unterdrückung von zu sendenden Paketen vornehmen. Diese Unterdrückung von Paketen sollte zufällig und mit einstellbarer Paketverlustwahrscheinlichkeit über das GUI erfolgen. Beispiel: der Wert 0,1 bedeutet, es werden im Mittel 10% der zu übertragenen Pakete unterdrückt. Passen dazu den Quelltext im Server an.

### 5. Anzeige von Statistiken am Client
Um die simulierten Netzwerkeigenschaften prüfen zu können und die Leistungsfähigkeit der später zu integrierenden Fehlerschutzcodes einschätzen zu können, ist eine Statistikanzeige notwendig.
Folgende Werte sollten mindestens am Client angezeigt werden:
1. Anzahl erhaltener/verlorener Medienpakete + prozentuale Angabe
2. Anzahl korrigierter/unkorrigierbarer Medienpakete
3. Die Anzeige sollte bis zum Ende des Videos sekündlich aktualisiert werden und dann auf dem Gesamtstand stehen bleiben.

Mit dem ersten Punkt kann die Qualität der Verbindung eingeschätzt werden und mit dem zweiten Punkt die Leistungsfähigkeit des FEC-Verfahrens.
Machen Sie sich Gedanken über weitere zu überwachende Parameter. Die meisten dieser Daten können aus dem FecHandler entnommen werden. Verifizieren Sie die korrekte Berechnung dieser Werte.


### 6. Implementierung des FEC-Schutzes

Implementieren Sie einen FEC-Schutz gemäß [RFC 5109](https://www.ietf.org/rfc/rfc5109.txt).
Der Server mit FEC-Schutz soll kompatibel zu Clients ohne FEC-Verfahren sein! Nutzen Sie dazu das Feld Payloadtype des RTP-Headers (PT=127 für FEC-Pakete).

Um nicht die komplette FEC-Funktionalität selbst entwickeln zu müssen, werden Ihnen zwei Klassen bereit gestellt:
1. [FECpacket](src/FECpacket.java): dies ist eine aus RTPpacket abgeleitete Klasse mit der erweiterten Funktionalität für das Handling von FEC-Paketen (vollständig implementiert)
2. [FecHandler](src/Fechandler.java): diese Klasse ist zuständig für die server- und clientseitige FEC-Bearbeitung unter Nutzung von FECpacket (teilweise implementiert)
   * Server: Kombination mehrerer Medienpakete zu einem FEC-Paket
   * Client: Jitterpuffer für empfangene Medien- und FEC-Pakete, Bereitstellung des aktuellen Bildinhaltes in Form einer Liste von RTP-Paketen mit gleichem TimeStamp.


Die Fehlerkorrektur im FecHandler ist noch zu implementieren. Dazu ist die vorhandene Architektur zu analysieren.
Die vorhandene Struktur ist in  [Architektur](#architekturvorschlag) beschrieben.
Um die Fehlerkorrektur im Client effizient zu implementieren, ist es sinnvoll, die `setRtp()`-Methode zu analysieren.

Alternativ können Sie die Klasse FecHandler auch komplett neu entwerfen und nur die fertige Klasse FECpacket übernehmen.





#### Architektur der Paketverarbeitung

##### Server
* der Server nimmt die gesamte Verarbeitung im vorhandenen Timer-Handler vor
* Nutzdaten speichern: `FecHandler.setRtp()`
* Nutzdaten senden
* Prüfung auf Erreichen der Gruppengröße: `FecHandler.isReady()`
* nach Ablauf des Gruppenzählers berechnetes FEC-Paket entgegennehmen und senden: `FecHandler.getPacket()`
* Kanalemulator jeweils für Medien- und FEC-Pakete aufrufen: `sendPacketWithError()`

##### Client
* Der Client nutzt getrennte Timer-Handler für den Empfang der Pakete und für das periodische Anzeigen der Bilder (keine Threads notwendig).
* Pakete empfangen per Timer
* Pakete im Jitterpuffer speichern:  `FecHandler.rcvRtpPacket()`
* Statistiken aktualisieren
* zur richtigen Zeit (Timeraufruf) das nächste Bild anzeigen: `FecHandler.getNextRtpList()`  Timer, welcher mit der fest eingestellten Abspielgeschwindigkeit läuft (25 Hz). 
* Verzögerung des Starts des Abspielens (ca. 2s), um den Jitterpuffer zu füllen

##### FecHandler
* Server: Hinzufügen eines RTP-Paketes zum FEC-Paket
* Speicherung der ankommenden Pakete in einer HashMap getrennt nach PayloadType, Zugriff über Sequenznummer
* Generierung einer Liste aller betroffenen RTP-Pakete für jedes FEC-Paket
* Speicherung der Sequenznummer des FEC-Packets und der Liste aller betroffenen RTP-Pakete für jedes RTP-Paket in zwei HashMaps (fecNr, fecList)
* Rückgabe einer Liste aller RTP-Pakete mit gleichem Timestamp
* ist ein RTP-Paket nicht vorhanden, dann Prüfung auf Korrigierbarkeit `checkCorrection()` und u.U. Korrektur `correctRTP()`
* periodisches Löschen alter nicht mehr benötigter Einträge im Jitterpuffer

##### FECpacket
* Ableitung aus vorhandenem RTPpacket
* Sender: Konstruktor zur Generierung eines FEC-Objektes aus Media-RTPs
* Empfänger: Konstruktur zur Generierung eines FEC-Objektes aus einem empfangenen FEC-RTP
* getRtpList: Ermittlung aller in einem FEC involvierten Media-RTPs
* getPacket: Holt komplettes FEC-Paket als Bytearray
* addRtp: fügt ein Media-RTP zum FEC-Objekt hinzu, inklusive aller notwendigen Berechnungen
* getLostRtp: generiert das verlorene Media-RTP aus den vorhandenen mittels addRtp hinzugefügten RTPs

#### Debugging
Es ist relativ unwahrscheinlich, dass das Programm auf Anhieb fehlerfrei funktioniert. Deshalb ist es wichtig, ein Konzept für die Fehlersuche zu entwickeln.
Hier einige Tipps für die Fehlersuche:
* Anzeige von Statusinformationen analog zu printheaders() des RTPpackets()
* Anzeige der ersten Bytes des Payload auf Sender und Empfänger
* prüfen des Senders auf korrekte Pakete
* Einstellung eines festen Seeds des Kanalsimulators für wiederholbare Versuche
* Test ohne bzw. mit Fehlerkorrektur
* Test der Anzahl verlorener / wiederhergestellter Pakete auf Plausibilität


### 7. Test und Dokumentation

#### Video
Binden Sie ein kurzes (ca. 0,5 - 2 min) Video Ihrer Wahl ein. Eine Umcodierung zu MJPEG kann zum Beispiel mittels VLC-Player erfolgen. Eventuell müssen Sie die Auflösung des Videos verringern, damit die Bilder jeweils in ein UDP-Paket passen.

#### Parameterwahl
Finden Sie den optimalen Wert für k bei einer Kanalverlustrate von 10%. Optimal bedeutet in diesem Fall eine subjektiv zufriedenstellende Bildqualität bei geringstmöglicher Redundanz.

#### Bestimmung der theoretisch zu erwartenden Verlustraten
Versuchen Sie, mathematisch die Paketverlustwahrscheinlichkeit (Restfehler) für verschiedene Gruppengrößen (k=2, 3, 5, 10, 20, 48) zu bestimmen.
Tragen Sie die Ergebnisse in einem Diagramm über der Kanalfehlerwahrscheinlichkeit auf. Sie können hierfür Gnuplot, R oder ein anderes Tool nutzen. Tragen Sie in das Diagramm zusätzlich mit Ihrem Videostreaming praktisch gemessene Fehlerhäufigkeiten auf (k=2 und k=48). Diskutieren Sie eventuelle Unterschiede zum theoretisch ermittelten Ergebnis.


#### Kompatibilität
Prüfen Sie die Kompatibilität des Clients und Servers mit dem VLC-Player und versuchen Sie eventuelle Probleme zu analysieren. Bei Problemen mit VLC 3 versuchen Sie VLC 2.2.


#### Dokumentation
Im Falle einer eigenen Architektur des FecHandlers ist eine Dokumentation notwendig, ansonsten nicht.
Beschreiben Sie die Architektur Ihrer Implementierung anhand sinnvoller Softwarebeschreibungsmethoden (Klassendiagramm, Zustandsdiagramm, etc.). Eine Quellcodekommentierung ist dazu nicht ausreichend!

#### Vorschläge
Manchen Sie konkrete Vorschläge zur Verbesserungen des Belegs.



## Literatur
* Real Time Streaming Protocol (RTSP)                   [RFC 2326](http://www.ietf.org/rfc/rfc2326.txt)
* SDP: Session Description Protocol                     [RFC2327](http://www.ietf.org/rfc/rfc2327.txt)
* RTP: A Transport Protocol for Real-Time Applications  [RFC 3550](http://www.ietf.org/rfc/rfc3550.txt)
* RTP Payload Format for JPEG-compressed Video          [RFC 2435](http://www.ietf.org/rfc/rfc2435.txt)
* RTP Profile for Audio and Video Conferences with Minimal Control  [RFC 3551](http://www.ietf.org/rfc/rfc3551.txt)
* RTP Payload Format for Generic Forward Error Correction  [RFC 5109](http://www.ietf.org/rfc/rfc5109.txt)
* Reed-Solomon Forward Error Correction (FEC) Schemes   [RFC 5510](http://www.ietf.org/rfc/rfc5510.txt)
* JPEG-Format [Link](https://de.wikipedia.org/wiki/JPEG_File_Interchange_Format)
* Diplomarbeit Karsten Pohl "Demonstration des RTSP-Videostreamings mittels VLC-Player und einer eigenen Implementierung"  [pdf](https://www2.htw-dresden.de/~jvogt/abschlussarbeiten/Pohl-Diplomarbeit.pdf)
* Diplomarbeit Elisa Zschorlich "Vergleich von Video-Streaming-Verfahren unter besonderer Berücksichtigung des Fehlerschutzes und Implementierung eines ausgewählten Verfahrens" [pdf](https://www2.htw-dresden.de/~jvogt/abschlussarbeiten/zschorlich-diplomarbeit.pdf)
