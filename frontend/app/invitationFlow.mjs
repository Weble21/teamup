export function invitationStep(preview) {
  if (preview.alreadyJoined) return 'joined';
  return preview.profileRegistered ? 'accept' : 'profile';
}
