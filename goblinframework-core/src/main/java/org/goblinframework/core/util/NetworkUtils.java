package org.goblinframework.core.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final public class NetworkUtils {

  private static final Pattern IP_PATTERN = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3,5}$");

  @NotNull
  public static String getLocalAddress() {
    InetAddress ia;
    try {
      ia = InetAddress.getLocalHost();
    } catch (Exception ex) {
      throw new GoblinNetworkException(ex);
    }
    if (isValidAddress(ia)) {
      return ia.getHostAddress();
    }
    return getLocalAddresses().stream()
        .findFirst().orElse("127.0.0.1");
  }

  @NotNull
  public static List<String> getLocalAddresses() {
    List<InetAddress> list = new ArrayList<>();
    try {
      InetAddress la = InetAddress.getLocalHost();
      if (isValidAddress(la)) {
        list.add(la);
      }
    } catch (Exception ex) {
      throw new GoblinNetworkException(ex);
    }
    Enumeration<NetworkInterface> it;
    try {
      it = NetworkInterface.getNetworkInterfaces();
    } catch (Exception ex) {
      throw new GoblinNetworkException(ex);
    }
    if (it == null) {
      return Collections.emptyList();
    }
    while (it.hasMoreElements()) {
      Enumeration<InetAddress> addresses = it.nextElement().getInetAddresses();
      while (addresses.hasMoreElements()) {
        InetAddress address = addresses.nextElement();
        if (isValidAddress(address)) {
          list.add(address);
        }
      }
    }
    return list.stream()
        .map(InetAddress::getHostAddress)
        .distinct()
        .collect(Collectors.toList());
  }

  private static boolean isValidAddress(@Nullable InetAddress address) {
    if (address == null || address.isLoopbackAddress())
      return false;
    if (address instanceof Inet6Address) {
      return false;
    }
    String name = address.getHostAddress();
    return (name != null
        && !"0.0.0.0".equals(name)
        && !"127.0.0.1".equals(name)
        && IP_PATTERN.matcher(name).matches());
  }
}
