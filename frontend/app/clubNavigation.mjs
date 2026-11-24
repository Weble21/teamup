// Resolve URL state against accessible clubs, never trust the ID in the URL alone.
export function requestedClub(search, clubs) {
  const id = new URLSearchParams(search).get('club');
  return id === null ? null : clubs.find(club => String(club.id) === id) ?? null;
}

export function clubPageUrl(href, clubId, guest = false) {
  const url = new URL(href);
  if (clubId != null) {
    url.searchParams.set('club', String(clubId));
    if (guest) url.searchParams.set('guest', '1');
    else url.searchParams.delete('guest');
  } else {
    url.searchParams.delete('club');
    url.searchParams.delete('guest');
  }
  return url.pathname + url.search + url.hash;
}
