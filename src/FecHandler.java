import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Jörg Vogt
 * @version 1.0
 */

/*
   Information according to RFC 5109
   http://apidocs.jitsi.org/libjitsi/
*/

public class FecHandler {
  RTPpacket rtp;
  FECpacket fec;

  // Receiver
  HashMap<Integer, RTPpacket> rtpStack = new HashMap<>(); // list of media packets
  HashMap<Integer, FECpacket> fecStack = new HashMap<>(); // list of fec packets
  HashMap<Integer, Integer> fecNr = new HashMap<>(); // Snr of corresponding fec packet
  HashMap<Integer, List<Integer>> fecList = new HashMap<>(); // list of involved media packets
  HashMap<Integer, List<Integer>> tsList = new HashMap<>(); // media packets with same ts

  int playCounter = 0; // SNr of RTP-packet to play next, initialized with first received packet

  // *** RTP-Header ************************ 
  static final int MJPEG = 26;
  int FEC_PT = 127; // Type for FEC
  int fecSeqNr; // Sender: increased by one, starting from 0
  int lastReceivedSeqNr; // Receiver: last received media packet

  // *** FEC Parameters -> Sender ************
  static final int maxGroupSize = 48;
  int fecGroupSize; // FEC group size
  int fecGroupCounter;

  // -> Receiver
  boolean useFec;

  // Error Concealment
  byte[] lastPayload = {1};

  // *** Statistics for media packets ********
  int nrReceived; // count only media at receiver
  int nrLost; // media missing, play loop
  int nrCorrected; // play loop
  int nrNotCorrected; // play loop
  int nrFramesRequested; // Video Frame
  int nrFramesLost; // Video Frame

  /** Constructor for Sender */
  public FecHandler(int size) {
    fecGroupSize = size;
  }

  /**
   * Client can choose using FEC or not
   *
   * @param useFec choose of using FEC
   */
  public FecHandler(boolean useFec) {
    this.useFec = useFec;
  }

  // *************** Sender SET *******************************************************************

  /**
   * *** Sender *** Saves the involved RTP packets to build the FEC packet
   *
   * @param rtp RTPpacket
   */
  public void setRtp(RTPpacket rtp) {
    // init new FEC packet if necessary

    System.out.println(fec == null);


    if (fec == null) {
      fec =
          new FECpacket(
              FEC_PT, fecSeqNr, rtp.gettimestamp(), fecGroupSize, rtp.getsequencenumber());
      fec.setUlpLevelHeader(0, 0, fecGroupSize);
    }

    fecGroupCounter++; // count the packets in the group
    fec.TimeStamp = rtp.gettimestamp(); // adjust the time stamp to the last packet in the group
    fec.addRtp(rtp);

    try{
      fec.printHeaders();
    }
    catch(Exception e){
      System.out.println("exception");
    }
  }

  /** @return True, if all RTP-packets of the group are handled */
  public boolean isReady() {
    return (fecGroupCounter == fecGroupSize);
  }

  /**
   * *** Sender *** Builds the FEC-RTP-Packet and resets the FEC-group
   *
   * @return Bitstream of FEC-Packet including RTP-Header
   */
  public byte[] getPacket() {

    System.out.println("FEC SeqNr: " + fecSeqNr);

    fec.printHeaders();
    // Adjust and reset all involved variables
    fecSeqNr++;
    fecGroupCounter = 0;
    byte[] buf = fec.getpacket();
    fec = null; // reset fec
    return buf;
  }

    /*
  HashMap<Integer, RTPpacket> rtpStack = new HashMap<>(); // list of media packets
  HashMap<Integer, FECpacket> fecStack = new HashMap<>(); // list of fec packets
  HashMap<Integer, Integer> fecNr = new HashMap<>(); // Snr of corresponding fec packet
  HashMap<Integer, List<Integer>> fecList = new HashMap<>(); // list of involved media packets
  HashMap<Integer, List<Integer>> tsList = new HashMap<>(); 
  */

  /** Reset of fec group and variables */
  private void clearSendGroup(int snr) {
    // TODO


    fecList.remove(snr);

  }

  /**
   * *** Sender *** Posibility to set the group at run time
   *
   * @param size FEC Group
   */
  public void setFecGroupSize(int size) {
    fecGroupSize = size;
  }

  // *************** Receiver PUT *****************************************************************

  /**
   * Handles and store a received media packet
   *
   * @param rtp the received RTP
   */
  public void rcvRtpPacket(RTPpacket rtp) {
    int seqNr = rtp.getsequencenumber();
    // if first packet set playcounter below seqNr
    if (rtpStack.size() == 0) playCounter = seqNr - 1;
    // separate Media an FEC
    if (rtp.getpayloadtype() == MJPEG) {
      nrReceived++; // count only media
      rtpStack.put(seqNr, rtp);
      lastReceivedSeqNr = seqNr;
      // create list of RTPs with same time stamp
      int ts = rtp.gettimestamp();
      List<Integer> list = tsList.get(ts);
      if (list == null) list = new ArrayList<>();
      list.add(seqNr);
      tsList.put( ts, list );
      System.out.println("FEC: set media nr: " + seqNr);
      System.out.println("FEC: set ts-list: " + (0xFFFFFFFFL & ts) + " " + list.toString());
    } else {
      rcvFecPacket(rtp);
    }
  }

  /**
   * Handles and store a recieved FEC packet
   *
   * @param rtp the received FEC-RTP
   */
  private void rcvFecPacket(RTPpacket rtp) {
    // build fec from rtp
    fec = new FECpacket(rtp.getpacket(), rtp.getpacket().length);
    // TASK remove comment for debugging
    fec.printHeaders();

    // stores fec
    int seqNrFec = fec.getsequencenumber();
    fecSeqNr = seqNrFec; // for deletion of fec storage
    fecStack.put(seqNrFec, fec);

    // get RTP List
    ArrayList<Integer> list = fec.getRtpList();
    System.out.println("FEC: set list: " + seqNrFec + " " + list.toString());

    // set list to get fec packet nr
    list.forEach((E) -> fecNr.put(E, seqNrFec)); // FEC-packet
    list.forEach((E) -> fecList.put(E, list));  // list of corresponding RTP packets
  }

  // *************** Receiver GET *****************************************************************

  /**
   * Delivers next Frame, depreciated, see getNextRtpList
   *
   * @return JPEG
   */
  public byte[] getNextFrame() {
    playCounter++;
    if (playCounter > lastReceivedSeqNr) {
      return null; // Jitter buffer is empty -> finish
    }
    RTPpacket rtp = getNextRtp();

    clearStack(playCounter); // reduce the stack

    if (rtp == null) {
      return lastPayload; // error concealment
    } else {
      lastPayload = rtp.getpayload();
      return rtp.getpayload();
    }
  }

  /**
   * Delivers next RTP packet,
   *
   * @return RTPpacket
   */
  private RTPpacket getNextRtp() {
    return getRtp(playCounter);
  }

  /**
   * Delivers a RTP packet
   *
   * @return RTPpacket
   */
  private RTPpacket getRtp(int snr) {
    snr = snr % 0x10000; // account overflow of SNr (16 Bit)
    RTPpacket rtp = rtpStack.get(snr);
    System.out.println("FEC: get RTP nr: " + snr);

    // check if correction is possible
    if (rtp == null) {
      System.out.println("FEC: Media lost: " + snr);
      nrLost++;

      System.out.println(useFec);

      if (checkCorrection(snr) && useFec) {
        nrCorrected++;
        System.out.println("---> FEC: correctable: " + snr);

        int fnr = fecNr.get(snr);
        FECpacket fpacket = fecStack.get(fnr);

        fpacket.printHeaders();

        ArrayList<Integer> rtpList = fpacket.getRtpList();

        System.out.println("fnr:");
        System.out.println(fnr);
        System.out.println("rtpList:");

        for(int rtpItem : rtpList) {

          System.out.print(rtpItem + " ");
        }

        System.out.println("");

        fpacket.printHeaders();

        //if(rtpList.size() == 0) return null;

        return fpacket.getLostRtp(snr);
      } else { 
        nrNotCorrected++;
        System.err.println("---> FEC: not correctable: " + snr);
        return null;
      }
    }
    return rtp;
  }

  /**
   * Delivers a set of RTPs with the same Time stamp the set is in the correct order concerning the
   * sequence number
   *
   * @return List
   */
  public ArrayList<RTPpacket> getNextRtpList() {
    nrFramesRequested++;
    playCounter++;
    ArrayList<RTPpacket> list = new ArrayList<>();
    RTPpacket rtp = getNextRtp();
    // check for lost rtp
    if (rtp == null) {
      nrFramesLost++;
      return null;
    }
    list.add(rtp);
    int ts = rtp.gettimestamp();
    List<Integer> rtpList = tsList.get(ts); // list of RTPs with same time stamp
    if (rtpList == null) return list; // if list is empty

    //TODO lost RTPs are not in the list but could perhaps be corrected -> check for snr
    //add all RTPs but the first which is already included
    for (int i = 1; i < rtpList.size(); i++) {
      list.add( getRtp(rtpList.get(i) ));
    }
    playCounter = playCounter + rtpList.size()-1; // set to snr of last packet
    //TODO if list is fragmented return null or implement JPEG error concealment

    System.out.println("-> Get list of " + list.size() + " RTPs with TS: " + (0xFFFFFFFFL & ts));
    return list;
  }

  /**
   * Checks if the RTP packet is reparable
   *
   * @param nr Sequence Nr.
   * @return true if possible
   */
  private boolean checkCorrection(int nr) {
    //TASK complete this method!

    System.out.println("checkCorrection");

    List<Integer> associatedPackets = fecList.get(nr);

    /*System.out.print("rtpStack: ");
    for(int key : rtpStack.keySet()) System.out.print(key + " ");
    System.out.println("");

    System.out.print("fecList: ");
    for(int key : fecList.keySet()) System.out.print(key + " ");
    System.out.println("");*/

    if(fecList.containsKey(nr) && fecNr.containsKey(nr)){
      System.out.print("associatedPackets: ");
      for(int key : associatedPackets) System.out.print(key + " ");
      System.out.println("");

      for(int i : associatedPackets){
        if(i == nr) continue;
        if(!rtpStack.containsKey(i)) return false;
      }
      return true;
    }
 
    return false;
  }

 
  /**
   * It is necessary to clear all data structures
   *
   * @param nr Media Sequence Nr.
   */
  private void clearStack(int nr) {
    //TASK complete this method!

    clearSendGroup(nr);

    int fnr = fecNr.get(nr);

    fecStack.remove(fnr); 
    fecNr.remove(fnr);

    System.out.println("clearStack");
  }

  // *************** Receiver Statistics ***********************************************************

  /**
   * @return Latest (highest) received sequence number
   */
  public int getSeqNr() {
    return lastReceivedSeqNr;
  }

  /**
   * @return Amount of received media packets (stored in jitter buffer)
   */
  public int getNrReceived() {
    return nrReceived;
  }

  /**
   * @return RTP-Snr of actual frame
   */
  public int getPlayCounter() {
    return playCounter;
  }

  /**
   * @return  Number of lost media packets (calculated at time of display)
   */
  public int getNrLost() {
    return nrLost;
  }

  /**
   * @return number of corrected media packets
   */
  public int getNrCorrected() {
    return nrCorrected;
  }

  /**
   * @return Number of nor correctable media packets
   */
  public int getNrNotCorrected() {
    return nrNotCorrected;
  }

  /**
   * @return Number of requested but lost Video frames
   */
  public int getNrFramesLost() { return nrFramesLost; }

  /**
   * @return Number of requested Video frames
   */
  public int getNrFramesRequested() {  return nrFramesRequested; }
}