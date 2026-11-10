package uz.heeyid.tafadapter.model.request;

/** TAF POST /api/accounts/v1/face/confirm/internal. The phone without +. */
public record TafFaceConfirmRequest(String pinfl, String phone) {}
